package com.careerplatform.ai.service;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.DuplicateStatus;
import com.careerplatform.ai.dto.JdConfirmRequirementRequest;
import com.careerplatform.ai.dto.JdParseAiResult;
import com.careerplatform.ai.dto.JdParseConfirmRequest;
import com.careerplatform.ai.dto.JdParseConfirmResponse;
import com.careerplatform.ai.dto.JdParseResponse;
import com.careerplatform.ai.dto.JdRequirementAiCandidate;
import com.careerplatform.ai.dto.SkillResolutionStatus;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.career.dto.JobRequirementResponse;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.entity.JobRequirement;
import com.careerplatform.career.enums.RequirementType;
import com.careerplatform.career.mapper.JobMapper;
import com.careerplatform.career.mapper.JobRequirementMapper;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.mapper.SkillMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdParseServiceTest {

    private static final Long JOB_ID = 7L;
    private static final Long USER_ID = 11L;

    @Mock
    private AiChatGateway aiChatGateway;
    @Mock
    private CareerService careerService;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobRequirementMapper jobRequirementMapper;
    @Mock
    private SkillMapper skillMapper;

    private JdParseService jdParseService;

    @BeforeEach
    void setUp() {
        jdParseService = new JdParseService(
                aiChatGateway,
                new JdParsePromptFactory(),
                careerService,
                jobMapper,
                jobRequirementMapper,
                skillMapper);
    }

    @Test
    void parseReturnsTypedEnrichedCandidatesAndDoesNotWriteDatabaseRows() {
        String rawJd = "Build Java services. A Bachelor's degree is preferred.";
        Skill java = skill(101L, "Java");
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job(rawJd));
        when(skillMapper.selectList(any())).thenReturn(List.of(java));
        when(jobRequirementMapper.selectList(any())).thenReturn(List.of());
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java", " java ", "Build Java services"),
                        candidate(RequirementType.EDUCATION, "Bachelor's degree", null, "Bachelor's degree")));

        JdParseResponse response = jdParseService.parse(JOB_ID, USER_ID);

        assertThat(response.sourceFingerprint()).isEqualTo(JdParseService.fingerprint(rawJd));
        assertThat(response.requirements()).hasSize(2);
        assertThat(response.requirements().get(0).matchedSkillId()).isEqualTo(101L);
        assertThat(response.requirements().get(0).matchedSkillName()).isEqualTo("Java");
        assertThat(response.requirements().get(0).resolutionStatus()).isEqualTo(SkillResolutionStatus.RESOLVED);
        assertThat(response.requirements().get(0).selected()).isTrue();
        assertThat(response.requirements().get(1).resolutionStatus()).isEqualTo(SkillResolutionStatus.NOT_APPLICABLE);
        assertThat(response.requirements().get(1).selected()).isTrue();

        ArgumentCaptor<String> userContent = ArgumentCaptor.forClass(String.class);
        verify(aiChatGateway).generateStructured(
                eq(new JdParsePromptFactory().systemInstruction()),
                userContent.capture(),
                eq(JdParseAiResult.class));
        assertThat(userContent.getValue()).contains(rawJd)
                .contains(JdParsePromptFactory.UNTRUSTED_START)
                .contains(JdParsePromptFactory.UNTRUSTED_END);
        verify(jobRequirementMapper, never()).insert(any(JobRequirement.class));
        verify(jobRequirementMapper, never()).update(any(), any());
        verify(jobRequirementMapper, never()).delete(any());
        verify(skillMapper, never()).insert(any(Skill.class));
        verify(careerService, never()).createRequirement(anyLong(), anyLong(), any());
    }

    @Test
    void ownerLookupFailureStopsParseBeforeCallingAi() {
        when(careerService.getJob(JOB_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("资源不存在"));

        assertThatThrownBy(() -> jdParseService.parse(JOB_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("资源不存在");

        verify(aiChatGateway, never()).generateStructured(any(), any(), any());
        verifyNoMapperWrites();
    }

    @Test
    void blankJdStopsParseBeforeCallingAi() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job(" \n\t"));

        assertThatThrownBy(() -> jdParseService.parse(JOB_ID, USER_ID))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("岗位原始 JD 不能为空");

        verify(aiChatGateway, never()).generateStructured(any(), any(), any());
        verifyNoMapperWrites();
    }

    @Test
    void unsupportedEvidenceIsDroppedAndExplainedAsWarning() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Only Java is listed."));
        when(skillMapper.selectList(any())).thenReturn(List.of());
        when(jobRequirementMapper.selectList(any())).thenReturn(List.of());
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(candidate(RequirementType.OTHER, "Remote work", null, "not present")));

        JdParseResponse response = jdParseService.parse(JOB_ID, USER_ID);

        assertThat(response.requirements()).isEmpty();
        assertThat(response.warnings())
                .containsExactly("已丢弃第1条候选：证据无法在当前 JD 中找到");
        verifyNoMapperWrites();
    }

    @Test
    void skillResolutionMarksUnknownSkillsWithoutCreatingDictionaryRows() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Java and UnresolvedSkill are required."));
        when(skillMapper.selectList(any())).thenReturn(List.of(skill(101L, "Java")));
        when(jobRequirementMapper.selectList(any())).thenReturn(List.of());
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java", "Java", "Java"),
                        candidate(RequirementType.SKILL, "UnresolvedSkill", "UnresolvedSkill", "UnresolvedSkill")));

        JdParseResponse response = jdParseService.parse(JOB_ID, USER_ID);

        assertThat(response.requirements()).extracting(r -> r.resolutionStatus())
                .containsExactly(SkillResolutionStatus.RESOLVED, SkillResolutionStatus.UNRESOLVED);
        assertThat(response.requirements().get(0).selected()).isTrue();
        assertThat(response.requirements().get(1).selected()).isFalse();
        assertThat(response.requirements().get(1).matchedSkillId()).isNull();
        verify(skillMapper, never()).insert(any(Skill.class));
        verify(jobRequirementMapper, never()).insert(any(JobRequirement.class));
    }

    @Test
    void aiDuplicateIsDroppedAndExistingRequirementIsMarkedNotSelected() {
        Skill java = skill(101L, "Java");
        JobRequirement existing = requirement(202L, RequirementType.SKILL, 101L, "Java backend");
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Java backend and degree required."));
        when(skillMapper.selectList(any())).thenReturn(List.of(java));
        when(jobRequirementMapper.selectList(any())).thenReturn(List.of(existing));
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class)))
                .thenReturn(aiResult(
                        candidate(RequirementType.SKILL, "Java backend", "Java", "Java backend"),
                        candidate(RequirementType.SKILL, "Java backend", "Java", "Java backend"),
                        candidate(RequirementType.EDUCATION, "degree", null, "degree")));

        JdParseResponse response = jdParseService.parse(JOB_ID, USER_ID);

        assertThat(response.requirements()).hasSize(2);
        assertThat(response.requirements().get(0).duplicateStatus()).isEqualTo(DuplicateStatus.DUPLICATE_EXISTING);
        assertThat(response.requirements().get(0).selected()).isFalse();
        assertThat(response.requirements().get(1).duplicateStatus()).isEqualTo(DuplicateStatus.NEW);
        assertThat(response.requirements().get(1).selected()).isTrue();
        assertThat(response.warnings()).containsExactly("已丢弃第2条候选：AI 返回了重复要求");
        verify(jobRequirementMapper, never()).insert(any(JobRequirement.class));
    }

    @Test
    void fingerprintIsLowercaseSha256() {
        assertThat(JdParseService.fingerprint("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void malformedAiResultUsesFoundationException() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Java"));
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class))).thenReturn(null);

        assertThatThrownBy(() -> jdParseService.parse(JOB_ID, USER_ID))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessage("AI 返回缺少 requirements");
        verifyNoMapperWrites();
    }

    @Test
    void excessiveAiOutputIsRejectedInsteadOfPersistedOrTruncatedSilently() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Java"));
        JdParseAiResult result = new JdParseAiResult();
        result.setRequirements(IntStream.range(0, 51)
                .mapToObj(index -> candidate(RequirementType.SKILL, "Java " + index, "Java", "Java"))
                .toList());
        result.setWarnings(List.of());
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class))).thenReturn(result);

        assertThatThrownBy(() -> jdParseService.parse(JOB_ID, USER_ID))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessage("AI 返回的岗位要求超过50条");
        verifyNoMapperWrites();
    }

    @Test
    void providerFailureUsesFoundationExceptionAndDoesNotWrite() {
        when(careerService.getJob(JOB_ID, USER_ID)).thenReturn(job("Java"));
        when(aiChatGateway.generateStructured(any(), any(), eq(JdParseAiResult.class)))
                .thenThrow(new AiProviderException("AI provider request failed"));

        assertThatThrownBy(() -> jdParseService.parse(JOB_ID, USER_ID))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI provider request failed");
        verifyNoMapperWrites();
    }

    @Test
    void confirmCreatesOnlySelectedItems() {
        String rawJd = "Java backend role";
        Job job = job(rawJd);
        when(jobMapper.selectOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(job);
        when(jobRequirementMapper.selectList(any())).thenReturn(List.of());
        JobRequirement created = requirement(303L, RequirementType.SKILL, 101L, "Java");
        when(careerService.createRequirement(eq(JOB_ID), eq(USER_ID), any())).thenReturn(created);

        JdConfirmRequirementRequest selected = confirmItem(true, RequirementType.SKILL, 101L, " Java ");
        JdConfirmRequirementRequest unselected = confirmItem(false, RequirementType.OTHER, null, "ignored");
        JdParseConfirmResponse response = jdParseService.confirm(
                JOB_ID, USER_ID, confirmRequest(rawJd, selected, unselected));

        assertThat(response.createdCount()).isEqualTo(1);
        assertThat(response.createdRequirements())
                .containsExactly(new JobRequirementResponse(303L, RequirementType.SKILL, 101L, "Java"));
        verify(careerService).createRequirement(eq(JOB_ID), eq(USER_ID), any());
    }

    @Test
    void confirmRejectsStaleFingerprintBeforeWrites() {
        when(jobMapper.selectOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(job("changed JD"));
        JdConfirmRequirementRequest item = confirmItem(true, RequirementType.OTHER, null, "requirement");

        assertThatThrownBy(() -> jdParseService.confirm(
                JOB_ID, USER_ID, confirmRequest("original JD", item)))
                .isInstanceOf(InvalidResourceStateException.class)
                .hasMessage("JD 已变化，请重新执行 AI 解析");

        verify(jobRequirementMapper, never()).selectList(any());
        verify(careerService, never()).createRequirement(anyLong(), anyLong(), any());
    }

    @Test
    void confirmRejectsSkillWithoutTrustedSkillIdBeforeWrites() {
        String rawJd = "Java role";
        when(jobMapper.selectOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(job(rawJd));
        JdConfirmRequirementRequest item = confirmItem(true, RequirementType.SKILL, null, "Java");

        assertThatThrownBy(() -> jdParseService.confirm(
                JOB_ID, USER_ID, confirmRequest(rawJd, item)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("技能要求必须关联技能");

        verify(careerService, never()).createRequirement(anyLong(), anyLong(), any());
    }

    private void verifyNoMapperWrites() {
        verify(jobRequirementMapper, never()).insert(any(JobRequirement.class));
        verify(jobRequirementMapper, never()).update(any(), any());
        verify(jobRequirementMapper, never()).delete(any());
        verify(skillMapper, never()).insert(any(Skill.class));
    }

    private Job job(String rawJd) {
        Job job = new Job();
        job.setId(JOB_ID);
        job.setUserId(USER_ID);
        job.setRawJd(rawJd);
        return job;
    }

    private Skill skill(Long id, String name) {
        Skill skill = new Skill();
        skill.setId(id);
        skill.setName(name);
        return skill;
    }

    private JobRequirement requirement(Long id, RequirementType type, Long skillId, String text) {
        JobRequirement requirement = new JobRequirement();
        requirement.setId(id);
        requirement.setJobId(JOB_ID);
        requirement.setRequirementType(type);
        requirement.setSkillId(skillId);
        requirement.setRequirementText(text);
        return requirement;
    }

    private JdParseAiResult aiResult(JdRequirementAiCandidate... candidates) {
        JdParseAiResult result = new JdParseAiResult();
        result.setRequirements(List.of(candidates));
        result.setWarnings(List.of());
        return result;
    }

    private JdRequirementAiCandidate candidate(
            RequirementType type, String description, String skillName, String evidence) {
        JdRequirementAiCandidate candidate = new JdRequirementAiCandidate();
        candidate.setType(type);
        candidate.setDescription(description);
        candidate.setSkillName(skillName);
        candidate.setEvidenceQuote(evidence);
        return candidate;
    }

    private JdParseConfirmRequest confirmRequest(String rawJd, JdConfirmRequirementRequest... items) {
        JdParseConfirmRequest request = new JdParseConfirmRequest();
        request.setSourceFingerprint(JdParseService.fingerprint(rawJd));
        request.setRequirements(List.of(items));
        return request;
    }

    private JdConfirmRequirementRequest confirmItem(
            boolean selected, RequirementType type, Long skillId, String text) {
        JdConfirmRequirementRequest item = new JdConfirmRequirementRequest();
        item.setSelected(selected);
        item.setRequirementType(type);
        item.setSkillId(skillId);
        item.setRequirementText(text);
        return item;
    }
}
