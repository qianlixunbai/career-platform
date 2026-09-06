package com.careerplatform.ai.service;

import com.careerplatform.ai.client.AiToolCallingGateway;
import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.dto.job.JobCandidate;
import com.careerplatform.ai.dto.job.JobDiscoveryAiResult;
import com.careerplatform.ai.dto.job.JobDiscoveryConfirmRequest;
import com.careerplatform.ai.dto.job.JobDiscoveryConfirmResponse;
import com.careerplatform.ai.dto.job.JobDiscoveryRequest;
import com.careerplatform.ai.dto.job.JobDiscoveryResponse;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiInvalidResponseException.Rule;
import com.careerplatform.ai.tool.JobSearchTool;
import com.careerplatform.career.dto.JobRequest;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.enums.JobType;
import com.careerplatform.career.enums.SourceType;
import com.careerplatform.career.entity.Job;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.common.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates read-only AI discovery and explicit, one-shot confirmation.
 * Discovery reconstructs all candidates from the request-local provider
 * session before atomically writing them to the ephemeral store.
 */
@Service
public class JobDiscoveryService {
    public static final int MAX_ADVICE_ITEMS = 10;
    public static final int MAX_ADVICE_TEXT_LENGTH = 1_000;
    public static final int MAX_WARNING_COUNT = 10;
    public static final int MAX_WARNING_LENGTH = 500;
    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_CITY_LENGTH = 100;
    public static final int MAX_RAW_JD_LENGTH = 16_000;

    private static final String EMPTY_RESULTS_WARNING = "未发现可靠的单个在招岗位候选。";
    private static final String RAW_JD_WARNING = "已保存岗位和来源链接，尚未录入完整 JD；搜索摘要不会作为完整 JD 保存。";

    private final AiToolCallingGateway aiToolCallingGateway;
    private final JobDiscoveryContextBuilder contextBuilder;
    private final JobDiscoveryPromptFactory promptFactory;
    private final JobSearchGateway jobSearchGateway;
    private final JobDiscoveryCandidateStore candidateStore;
    private final CareerService careerService;
    private final TransactionTemplate confirmationTransaction;

    /** Spring constructor. Confirmation always runs in a new transaction. */
    @Autowired
    public JobDiscoveryService(AiToolCallingGateway aiToolCallingGateway,
                               JobDiscoveryContextBuilder contextBuilder,
                               JobDiscoveryPromptFactory promptFactory,
                               JobSearchGateway jobSearchGateway,
                               JobDiscoveryCandidateStore candidateStore,
                               CareerService careerService,
                               PlatformTransactionManager transactionManager) {
        this(aiToolCallingGateway, contextBuilder, promptFactory, jobSearchGateway,
                candidateStore, careerService, newRequiresNewTemplate(transactionManager));
    }

    /** Deterministic unit-test seam when a transaction template is supplied. */
    public JobDiscoveryService(AiToolCallingGateway aiToolCallingGateway,
                               JobDiscoveryContextBuilder contextBuilder,
                               JobDiscoveryPromptFactory promptFactory,
                               JobSearchGateway jobSearchGateway,
                               JobDiscoveryCandidateStore candidateStore,
                               CareerService careerService,
                               TransactionTemplate confirmationTransaction) {
        this.aiToolCallingGateway = Objects.requireNonNull(aiToolCallingGateway,
                "aiToolCallingGateway must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory must not be null");
        this.jobSearchGateway = Objects.requireNonNull(jobSearchGateway, "jobSearchGateway must not be null");
        this.candidateStore = Objects.requireNonNull(candidateStore, "candidateStore must not be null");
        this.careerService = Objects.requireNonNull(careerService, "careerService must not be null");
        this.confirmationTransaction = Objects.requireNonNull(confirmationTransaction,
                "confirmationTransaction must not be null");
        this.confirmationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Run one read-only discovery flow. The only mutable state created here is
     * request-local search session/tool state and ephemeral candidate storage.
     */
    public JobDiscoveryResponse discover(Long userId, JobDiscoveryRequest request) {
        JobDiscoveryContextBuilder.DiscoveryContext context = contextBuilder.build(userId, request);

        // Fail closed before spending an AI call when the configured provider
        // cannot service the request. Context ownership has already been
        // checked above, and no provider call is made on an owner failure.
        jobSearchGateway.requireAvailable();

        JobSearchSession session = new JobSearchSession();
        JobSearchTool searchTool = new JobSearchTool(jobSearchGateway, session);
        JobDiscoveryAiResult aiResult = aiToolCallingGateway.discover(
                promptFactory.systemInstruction(), promptFactory.userContent(context), searchTool);

        List<String> warnings = new WarningCollector()
                .addAll(context.warnings())
                .addAll(validateProviderWarnings(aiResult))
                .values();
        List<JobCandidate> reconstructed = reconstructCandidates(context, session, aiResult);
        if (reconstructed.isEmpty()) {
            warnings = new WarningCollector().addAll(warnings).add(EMPTY_RESULTS_WARNING).values();
        }

        // Reconstruct every candidate before this call. An invalid model
        // result therefore cannot leave a partially populated store.
        List<JobCandidate> stored = candidateStore.saveAll(userId, reconstructed);
        return new JobDiscoveryResponse(stored, warnings, searchTool.getCallCount());
    }

    /** Confirm a candidate without invoking AI or the external search tool. */
    public JobDiscoveryConfirmResponse confirm(Long userId, JobDiscoveryConfirmRequest request) {
        validateConfirmRequest(request);
        return candidateStore.confirm(userId, request.candidateId(), candidate -> {
            JobRequest jobRequest = toJobRequest(candidate, request);
            Job created = confirmationTransaction.execute(status -> {
                Job job = careerService.createJob(userId, jobRequest);
                if (job == null || job.getId() == null) {
                    throw new IllegalStateException("岗位保存未返回有效岗位");
                }
                return job;
            });
            List<String> warnings = isBlank(request.rawJd())
                    ? List.of(RAW_JD_WARNING) : List.of();
            return new JobDiscoveryConfirmResponse(created.getId(), warnings);
        });
    }

    private List<JobCandidate> reconstructCandidates(
            JobDiscoveryContextBuilder.DiscoveryContext context,
            JobSearchSession session,
            JobDiscoveryAiResult aiResult) {
        if (aiResult == null || aiResult.candidates() == null) {
            throw invalid(Rule.CANDIDATES_NULL, "AI 返回的候选列表为空");
        }
        List<JobDiscoveryAiResult.Advice> adviceList = aiResult.candidates();
        if (adviceList.size() > context.maxCandidates()) {
            throw invalid(Rule.CANDIDATE_COUNT_EXCEEDED, "AI 返回的候选数量超过本次请求上限");
        }

        Set<String> resultKeys = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        List<JobCandidate> candidates = new ArrayList<>(adviceList.size());
        for (int index = 0; index < adviceList.size(); index++) {
            JobDiscoveryAiResult.Advice advice = adviceList.get(index);
            validateAdvice(advice, index, context.maxCandidates(), resultKeys, ranks);

            JobSearchGateway.ProviderSearchResult providerResult = session.resolve(advice.resultKey().trim());
            if (providerResult == null) {
                throw invalid(Rule.UNKNOWN_RESULT_KEY, "AI 引用了未知搜索结果");
            }
            JobCandidate.SourceFacts sourceFacts = toSourceFacts(providerResult);
            JobCandidate.ExtractedFields extractedFields = new JobCandidate.ExtractedFields(
                    boundedTitle(providerResult.sourceTitle()),
                    null,
                    context.location(),
                    null);
            JobCandidate.AiAdvice aiAdvice = new JobCandidate.AiAdvice(
                    advice.rank(),
                    requiredAdviceText(advice.fitSummary(), "fitSummary"),
                    boundedAdviceList(advice.strengths(), "strengths"),
                    boundedAdviceList(advice.gaps(), "gaps"),
                    boundedAdviceList(advice.uncertainty(), "uncertainty"),
                    resolveMatchedSkills(advice.matchedSkillKeys(), context.skillNamesByKey()));
            candidates.add(new JobCandidate(null, null, sourceFacts, extractedFields, aiAdvice));
        }
        return List.copyOf(candidates);
    }

    private JobCandidate.SourceFacts toSourceFacts(JobSearchGateway.ProviderSearchResult providerResult) {
        if (providerResult == null || isBlank(providerResult.sourceUrl())) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "搜索结果缺少来源链接");
        }
        URI uri = parseSafeHttpUri(providerResult.sourceUrl());
        String sourceHost = isBlank(providerResult.sourceHost())
                ? uri.getHost() : providerResult.sourceHost().trim();
        if (isBlank(sourceHost) || sourceHost.length() > 150) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "搜索结果来源网站无效");
        }
        if (providerResult.sourceUrl().length() > 500) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "搜索结果来源链接过长");
        }
        return new JobCandidate.SourceFacts(
                providerResult.sourceUrl(),
                boundedSourceText(providerResult.sourceTitle(), 500),
                sourceHost,
                boundedSourceText(providerResult.sourceSnippet(), 2_000),
                providerResult.publishedAt(),
                "TAVILY");
    }

    private JobRequest toJobRequest(JobCandidate candidate, JobDiscoveryConfirmRequest request) {
        if (candidate == null || candidate.sourceFacts() == null) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "候选来源事实无效");
        }
        JobCandidate.SourceFacts facts = candidate.sourceFacts();
        URI sourceUri = parseSafeHttpUri(facts.sourceUrl());
        String sourceHost = isBlank(facts.sourceHost()) ? sourceUri.getHost() : facts.sourceHost().trim();
        if (isBlank(sourceHost) || sourceHost.length() > 150) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "候选来源网站无效");
        }

        JobRequest jobRequest = new JobRequest();
        jobRequest.setCompanyId(request.companyId());
        jobRequest.setTitle(request.title().trim());
        jobRequest.setCity(blankToNull(request.city()));
        jobRequest.setJobType(request.jobType());
        // Provider dates are copied only when explicitly present; no date is
        // inferred from a title, snippet, or model advice.
        jobRequest.setPublishDate(facts.publishedAt());
        jobRequest.setDeadline(null);
        // A search snippet is never promoted to a full raw JD. This value is
        // exclusively the user-provided confirmation field.
        jobRequest.setRawJd(blankToNull(request.rawJd()));
        jobRequest.setSourceType(SourceType.OTHER);
        jobRequest.setSourceName(sourceHost);
        jobRequest.setSourceUrl(facts.sourceUrl());
        return jobRequest;
    }

    private static void validateAdvice(JobDiscoveryAiResult.Advice advice,
                                       int index,
                                       int maxCandidates,
                                       Set<String> resultKeys,
                                       Set<Integer> ranks) {
        if (advice == null) {
            throw invalid(Rule.CANDIDATE_INVALID, "AI 返回的第" + (index + 1) + "个候选为空");
        }
        String resultKey = requiredAdviceText(advice.resultKey(), "resultKey");
        if (!resultKeys.add(resultKey)) {
            throw invalid(Rule.RESULT_KEY_DUPLICATE, "AI 返回了重复的搜索结果");
        }
        if (advice.rank() == null || advice.rank() < 1 || advice.rank() > maxCandidates
                || !ranks.add(advice.rank())) {
            throw invalid(Rule.RANK_INVALID, "AI 返回的候选排序无效");
        }
        // Validate all bounded fields before reconstruction, including fields
        // that will later be filtered against the canonical skill whitelist.
        requiredAdviceText(advice.fitSummary(), "fitSummary");
        validateAdviceList(advice.strengths(), "strengths");
        validateAdviceList(advice.gaps(), "gaps");
        validateAdviceList(advice.uncertainty(), "uncertainty");
        validateAdviceList(advice.matchedSkillKeys(), "matchedSkillKeys");
    }

    private static List<String> validateProviderWarnings(JobDiscoveryAiResult result) {
        if (result == null) {
            throw invalid(Rule.SERVICE_RESULT_NULL, "AI 返回为空");
        }
        List<String> warnings = result.warnings();
        if (warnings == null) {
            return List.of();
        }
        if (warnings.size() > MAX_WARNING_COUNT) {
            throw invalid(Rule.WARNINGS_COUNT_EXCEEDED, "AI 返回的警告数量超过上限");
        }
        List<String> bounded = new ArrayList<>(warnings.size());
        for (String warning : warnings) {
            if (warning == null || warning.isBlank() || warning.trim().length() > MAX_WARNING_LENGTH) {
                throw invalid(Rule.WARNING_INVALID, "AI 返回的警告无效");
            }
            bounded.add(warning.trim());
        }
        return List.copyOf(bounded);
    }

    private static void validateAdviceList(List<String> values, String field) {
        if (values == null) {
            throw invalid(Rule.ADVICE_LIST_NULL, "AI 返回的" + field + "为空");
        }
        if (values.size() > MAX_ADVICE_ITEMS) {
            throw invalid(Rule.ADVICE_LIST_COUNT_EXCEEDED, "AI 返回的" + field + "数量超过上限");
        }
        for (String value : values) {
            if (value == null || value.isBlank() || value.trim().length() > MAX_ADVICE_TEXT_LENGTH) {
                throw invalid(Rule.ADVICE_ITEM_INVALID, "AI 返回的" + field + "内容无效");
            }
        }
    }

    private static List<String> boundedAdviceList(List<String> values, String field) {
        validateAdviceList(values, field);
        return values.stream().map(String::trim).toList();
    }

    private static List<JobCandidate.MatchedSkill> resolveMatchedSkills(
            List<String> keys, Map<String, String> canonicalSkillNames) {
        validateAdviceList(keys, "matchedSkillKeys");
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        List<JobCandidate.MatchedSkill> resolved = new ArrayList<>();
        for (String key : keys) {
            String normalized = key.trim();
            if (!distinct.add(normalized)) {
                continue;
            }
            String name = canonicalSkillNames.get(normalized);
            if (name != null) {
                resolved.add(new JobCandidate.MatchedSkill(normalized, name));
            }
        }
        return List.copyOf(resolved);
    }

    private static URI parseSafeHttpUri(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank() || sourceUrl.length() > 500) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "来源链接无效");
        }
        try {
            URI uri = new URI(sourceUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || host == null || host.isBlank() || uri.getUserInfo() != null
                    || "localhost".equalsIgnoreCase(host)
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".localhost")
                    || host.toLowerCase(java.util.Locale.ROOT).endsWith(".local")) {
                throw invalid(Rule.SOURCE_FACTS_INVALID, "来源链接无效");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(Rule.SOURCE_FACTS_INVALID, "来源链接无效");
        }
    }

    private static String boundedTitle(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= MAX_TITLE_LENGTH
                ? normalized : normalized.substring(0, MAX_TITLE_LENGTH);
    }

    private static String boundedSourceText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static void validateConfirmRequest(JobDiscoveryConfirmRequest request) {
        if (request == null || isBlank(request.candidateId())) {
            throw new InvalidRequestException("候选岗位不能为空");
        }
        if (request.candidateId().trim().length() > 100) {
            throw new InvalidRequestException("候选岗位标识无效");
        }
        if (request.companyId() == null) {
            throw new InvalidRequestException("公司不能为空");
        }
        if (isBlank(request.title()) || request.title().trim().length() > MAX_TITLE_LENGTH) {
            throw new InvalidRequestException("岗位名称无效");
        }
        if (request.city() != null && request.city().trim().length() > MAX_CITY_LENGTH) {
            throw new InvalidRequestException("城市长度不能超过100个字符");
        }
        if (request.jobType() == null) {
            throw new InvalidRequestException("岗位类型不能为空");
        }
        if (request.rawJd() != null && request.rawJd().length() > MAX_RAW_JD_LENGTH) {
            throw new InvalidRequestException("岗位描述长度不能超过16000个字符");
        }
    }

    private static String requiredAdviceText(String value, String field) {
        if (isBlank(value) || value.trim().length() > MAX_ADVICE_TEXT_LENGTH) {
            throw invalid(Rule.ADVICE_ITEM_INVALID, "AI 返回的" + field + "无效");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static AiInvalidResponseException invalid(Rule rule, String message) {
        return new AiInvalidResponseException(rule, message);
    }

    private static TransactionTemplate newRequiresNewTemplate(PlatformTransactionManager manager) {
        Objects.requireNonNull(manager, "transactionManager must not be null");
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static final class WarningCollector {
        private final LinkedHashSet<String> values = new LinkedHashSet<>();

        WarningCollector add(String value) {
            if (!isBlank(value) && values.size() < MAX_WARNING_COUNT) {
                String normalized = value.trim();
                values.add(normalized.length() <= MAX_WARNING_LENGTH
                        ? normalized : normalized.substring(0, MAX_WARNING_LENGTH));
            }
            return this;
        }

        WarningCollector addAll(List<String> values) {
            if (values != null) {
                values.forEach(this::add);
            }
            return this;
        }

        List<String> values() {
            return List.copyOf(values);
        }
    }
}
