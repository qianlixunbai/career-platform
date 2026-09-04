package com.careerplatform.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.DuplicateStatus;
import com.careerplatform.ai.dto.JdConfirmRequirementRequest;
import com.careerplatform.ai.dto.JdParseAiResult;
import com.careerplatform.ai.dto.JdParseConfirmRequest;
import com.careerplatform.ai.dto.JdParseConfirmResponse;
import com.careerplatform.ai.dto.JdParseResponse;
import com.careerplatform.ai.dto.JdRequirementAiCandidate;
import com.careerplatform.ai.dto.JdRequirementCandidateResponse;
import com.careerplatform.ai.dto.SkillResolutionStatus;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.career.dto.JobRequirementRequest;
import com.careerplatform.career.dto.JobRequirementResponse;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.entity.JobRequirement;
import com.careerplatform.career.enums.RequirementType;
import com.careerplatform.career.mapper.JobMapper;
import com.careerplatform.career.mapper.JobRequirementMapper;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.common.exception.DuplicateResourceException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.mapper.SkillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class JdParseService {
    private static final Logger log = LoggerFactory.getLogger(JdParseService.class);
    private static final int MAX_REQUIREMENTS = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;
    private static final int MAX_SKILL_NAME_LENGTH = 100;
    private static final int MAX_EVIDENCE_LENGTH = 200;
    private static final int MAX_WARNINGS = 20;
    private static final int MAX_WARNING_LENGTH = 500;

    private final AiChatGateway aiChatGateway;
    private final JdParsePromptFactory promptFactory;
    private final CareerService careerService;
    private final JobMapper jobMapper;
    private final JobRequirementMapper jobRequirementMapper;
    private final SkillMapper skillMapper;

    public JdParseService(AiChatGateway aiChatGateway,
                          JdParsePromptFactory promptFactory,
                          CareerService careerService,
                          JobMapper jobMapper,
                          JobRequirementMapper jobRequirementMapper,
                          SkillMapper skillMapper) {
        this.aiChatGateway = aiChatGateway;
        this.promptFactory = promptFactory;
        this.careerService = careerService;
        this.jobMapper = jobMapper;
        this.jobRequirementMapper = jobRequirementMapper;
        this.skillMapper = skillMapper;
    }

    public JdParseResponse parse(Long jobId, Long userId) {
        Job job = careerService.getJob(jobId, userId);
        String rawJd = requireRawJd(job.getRawJd());
        log.info("JD parse requested for jobId={} userId={}", jobId, userId);

        JdParseAiResult aiResult = aiChatGateway.generateStructured(
                promptFactory.systemInstruction(),
                promptFactory.userContent(rawJd),
                JdParseAiResult.class);
        JdParseResponse response = validateAndEnrich(rawJd, jobId, aiResult);
        log.info("JD parse completed for jobId={} candidateCount={}", jobId, response.requirements().size());
        return response;
    }

    @Transactional
    public JdParseConfirmResponse confirm(Long jobId, Long userId, JdParseConfirmRequest request) {
        Job lockedJob = jobMapper.selectOwnedForUpdate(jobId, userId);
        if (lockedJob == null) {
            throw new ResourceNotFoundException("资源不存在");
        }
        String rawJd = requireRawJd(lockedJob.getRawJd());
        if (!fingerprint(rawJd).equals(request.getSourceFingerprint())) {
            throw new InvalidResourceStateException("JD 已变化，请重新执行 AI 解析");
        }

        List<JdConfirmRequirementRequest> selected = request.getRequirements().stream()
                .filter(item -> Boolean.TRUE.equals(item.getSelected()))
                .toList();
        validateNoDuplicates(jobId, selected);

        List<JobRequirementResponse> created = new ArrayList<>();
        for (JdConfirmRequirementRequest item : selected) {
            JobRequirementRequest requirementRequest = new JobRequirementRequest();
            requirementRequest.setRequirementType(item.getRequirementType());
            requirementRequest.setSkillId(item.getRequirementType() == RequirementType.SKILL ? item.getSkillId() : null);
            requirementRequest.setRequirementText(item.getRequirementText().trim());
            JobRequirement value = careerService.createRequirement(jobId, userId, requirementRequest);
            created.add(toResponse(value));
        }
        return new JdParseConfirmResponse(created.size(), List.copyOf(created));
    }

    static String fingerprint(String rawJd) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawJd.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JdParseResponse validateAndEnrich(String rawJd, Long jobId, JdParseAiResult aiResult) {
        if (aiResult == null || aiResult.getRequirements() == null) {
            throw invalidAiResponse("AI 返回缺少 requirements");
        }
        if (aiResult.getRequirements().size() > MAX_REQUIREMENTS) {
            throw invalidAiResponse("AI 返回的岗位要求超过50条");
        }

        List<String> warnings = validateWarnings(aiResult.getWarnings());
        String normalizedJd = normalize(rawJd);
        Map<String, Skill> skillsByName = new HashMap<>();
        for (Skill skill : skillMapper.selectList(null)) {
            skillsByName.putIfAbsent(normalize(skill.getName()), skill);
        }
        Set<String> existingKeys = existingRequirementKeys(jobId);
        Set<String> seenAiKeys = new HashSet<>();
        List<JdRequirementCandidateResponse> candidates = new ArrayList<>();

        for (int index = 0; index < aiResult.getRequirements().size(); index++) {
            JdRequirementAiCandidate item = aiResult.getRequirements().get(index);
            validateAiCandidate(item, index);
            String description = item.getDescription().trim();
            String evidence = item.getEvidenceQuote().trim();
            String skillName = blankToNull(item.getSkillName());

            if (!normalizedJd.contains(normalize(evidence))) {
                warnings.add("已丢弃第" + (index + 1) + "条候选：证据无法在当前 JD 中找到");
                continue;
            }

            String aiKey = aiCandidateKey(item.getType(), description, skillName);
            if (!seenAiKeys.add(aiKey)) {
                warnings.add("已丢弃第" + (index + 1) + "条候选：AI 返回了重复要求");
                continue;
            }

            Skill matchedSkill = item.getType() == RequirementType.SKILL
                    ? skillsByName.get(normalize(skillName)) : null;
            SkillResolutionStatus resolution = item.getType() != RequirementType.SKILL
                    ? SkillResolutionStatus.NOT_APPLICABLE
                    : matchedSkill == null ? SkillResolutionStatus.UNRESOLVED : SkillResolutionStatus.RESOLVED;
            String existingKey = requirementKey(item.getType(), description,
                    matchedSkill == null ? null : matchedSkill.getId());
            DuplicateStatus duplicate = existingKeys.contains(existingKey)
                    ? DuplicateStatus.DUPLICATE_EXISTING : DuplicateStatus.NEW;
            boolean selected = resolution != SkillResolutionStatus.UNRESOLVED
                    && duplicate == DuplicateStatus.NEW;

            candidates.add(new JdRequirementCandidateResponse(
                    item.getType(), description, skillName, evidence,
                    matchedSkill == null ? null : matchedSkill.getId(),
                    matchedSkill == null ? null : matchedSkill.getName(),
                    resolution, duplicate, selected));
        }
        if (warnings.size() > MAX_WARNINGS) {
            warnings = new ArrayList<>(warnings.subList(0, MAX_WARNINGS));
        }
        return new JdParseResponse(fingerprint(rawJd), List.copyOf(candidates), List.copyOf(warnings));
    }

    private void validateAiCandidate(JdRequirementAiCandidate item, int index) {
        if (item == null || item.getType() == null) {
            throw invalidAiResponse("AI 返回的第" + (index + 1) + "条要求缺少类型");
        }
        if (isBlank(item.getDescription()) || item.getDescription().trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw invalidAiResponse("AI 返回的第" + (index + 1) + "条要求内容无效");
        }
        if (isBlank(item.getEvidenceQuote()) || item.getEvidenceQuote().trim().length() > MAX_EVIDENCE_LENGTH) {
            throw invalidAiResponse("AI 返回的第" + (index + 1) + "条证据无效");
        }
        String skillName = blankToNull(item.getSkillName());
        if (item.getType() == RequirementType.SKILL) {
            if (skillName == null || skillName.length() > MAX_SKILL_NAME_LENGTH) {
                throw invalidAiResponse("AI 返回的第" + (index + 1) + "条技能名称无效");
            }
        } else if (skillName != null) {
            throw invalidAiResponse("AI 返回的非技能要求不能包含技能名称");
        }
    }

    private List<String> validateWarnings(List<String> aiWarnings) {
        List<String> warnings = new ArrayList<>();
        if (aiWarnings == null) {
            return warnings;
        }
        if (aiWarnings.size() > MAX_WARNINGS) {
            throw invalidAiResponse("AI 返回的 warnings 过多");
        }
        for (String warning : aiWarnings) {
            if (isBlank(warning) || warning.trim().length() > MAX_WARNING_LENGTH) {
                throw invalidAiResponse("AI 返回的 warning 无效");
            }
            warnings.add(warning.trim());
        }
        return warnings;
    }

    private void validateNoDuplicates(Long jobId, List<JdConfirmRequirementRequest> selected) {
        Set<String> keys = new LinkedHashSet<>(existingRequirementKeys(jobId));
        for (JdConfirmRequirementRequest item : selected) {
            if (item.getRequirementType() == RequirementType.SKILL && item.getSkillId() == null) {
                throw new InvalidRequestException("技能要求必须关联技能");
            }
            if (item.getRequirementType() != RequirementType.SKILL && item.getSkillId() != null) {
                throw new InvalidRequestException("非技能要求不能关联技能");
            }
            String key = requirementKey(item.getRequirementType(), item.getRequirementText(), item.getSkillId());
            if (!keys.add(key)) {
                throw new DuplicateResourceException("确认内容包含重复的岗位要求");
            }
        }
    }

    private Set<String> existingRequirementKeys(Long jobId) {
        Set<String> keys = new HashSet<>();
        List<JobRequirement> existing = jobRequirementMapper.selectList(
                new LambdaQueryWrapper<JobRequirement>().eq(JobRequirement::getJobId, jobId));
        for (JobRequirement requirement : existing) {
            keys.add(requirementKey(requirement.getRequirementType(), requirement.getRequirementText(), requirement.getSkillId()));
        }
        return keys;
    }

    private static String aiCandidateKey(RequirementType type, String description, String skillName) {
        return type.name() + "|" + normalize(description) + "|" + normalize(skillName);
    }

    private static String requirementKey(RequirementType type, String text, Long skillId) {
        return type.name() + "|" + normalize(text) + "|" + (type == RequirementType.SKILL ? skillId : "-");
    }

    private static String requireRawJd(String rawJd) {
        if (isBlank(rawJd)) {
            throw new InvalidRequestException("岗位原始 JD 不能为空");
        }
        if (rawJd.length() > 16000) {
            throw new InvalidRequestException("岗位描述长度不能超过16000个字符");
        }
        return rawJd;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static AiInvalidResponseException invalidAiResponse(String message) {
        return new AiInvalidResponseException(message);
    }

    private static JobRequirementResponse toResponse(JobRequirement value) {
        return new JobRequirementResponse(value.getId(), value.getRequirementType(), value.getSkillId(), value.getRequirementText());
    }
}
