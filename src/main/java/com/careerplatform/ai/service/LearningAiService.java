package com.careerplatform.ai.service;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.learning.LearningAiEvidence;
import com.careerplatform.ai.dto.learning.LearningAiReviewItem;
import com.careerplatform.ai.dto.learning.LearningAiReviewItemResult;
import com.careerplatform.ai.dto.learning.LearningPlanAiResult;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionResponse;
import com.careerplatform.ai.dto.learning.LearningPlanAiTaskResult;
import com.careerplatform.ai.dto.learning.LearningPlanAiTaskSuggestion;
import com.careerplatform.ai.dto.learning.LearningReviewAiResult;
import com.careerplatform.ai.dto.learning.LearningReviewAiSuggestionResponse;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only AI suggestion service for Learning plan and weekly review candidates. */
@Service
public class LearningAiService {
    private static final int MIN_PLAN_TASKS = 2;
    private static final int MAX_PLAN_TASKS = 6;
    private static final int MAX_PLAN_GOAL_LENGTH = 500;
    private static final int MAX_RATIONALE_LENGTH = 2_000;
    private static final int MAX_TASK_TITLE_LENGTH = 200;
    private static final int MAX_TASK_DESCRIPTION_LENGTH = 2_000;
    private static final int MAX_WARNING_LENGTH = 500;
    private static final int MAX_WARNINGS = 20;
    private static final int MAX_REVIEW_TEXT_LENGTH = 2_000;
    private static final int MAX_REVIEW_ITEMS = 12;

    private final AiChatGateway aiChatGateway;
    private final LearningAiContextBuilder contextBuilder;
    private final LearningAiPromptFactory promptFactory;

    public LearningAiService(AiChatGateway aiChatGateway,
                             LearningAiContextBuilder contextBuilder,
                             LearningAiPromptFactory promptFactory) {
        this.aiChatGateway = Objects.requireNonNull(aiChatGateway, "aiChatGateway must not be null");
        this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
        this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory must not be null");
    }

    /** Generate a validated ephemeral plan candidate. This method performs no writes. */
    public LearningPlanAiSuggestionResponse suggestPlan(Long userId,
                                                        LearningPlanAiSuggestionRequest request) {
        LearningAiContextBuilder.PlanContext context = contextBuilder.buildPlanContext(userId, request);
        LearningPlanAiResult result = aiChatGateway.generateStructured(
                promptFactory.systemInstruction(),
                promptFactory.planUserContent(context),
                LearningPlanAiResult.class);
        return validatePlanResult(context, result);
    }

    /** Alias for callers using the endpoint's noun-first terminology. */
    public LearningPlanAiSuggestionResponse planSuggestion(Long userId,
                                                           LearningPlanAiSuggestionRequest request) {
        return suggestPlan(userId, request);
    }

    /** Generate a validated ephemeral review candidate after owner validation and Java metrics. */
    public LearningReviewAiSuggestionResponse suggestReview(Long planId, Long userId) {
        LearningAiContextBuilder.ReviewContext context = contextBuilder.buildReviewContext(userId, planId);
        LearningReviewAiResult result = aiChatGateway.generateStructured(
                promptFactory.systemInstruction(),
                promptFactory.reviewUserContent(context),
                LearningReviewAiResult.class);
        return validateReviewResult(context, result);
    }

    /** Explicitly named alias when a caller places the user id before the plan id. */
    public LearningReviewAiSuggestionResponse suggestReviewForUser(Long userId, Long planId) {
        return suggestReview(planId, userId);
    }

    /** Alias matching the endpoint's noun-first terminology. */
    public LearningReviewAiSuggestionResponse reviewSuggestion(Long planId, Long userId) {
        return suggestReview(planId, userId);
    }

    private LearningPlanAiSuggestionResponse validatePlanResult(
            LearningAiContextBuilder.PlanContext context,
            LearningPlanAiResult result) {
        if (result == null) {
            throw invalid("AI 返回为空");
        }
        String mainGoal = requiredText(result.getMainGoal(), MAX_PLAN_GOAL_LENGTH, "mainGoal");
        String rationale = boundedText(result.getRationale(), MAX_RATIONALE_LENGTH, "rationale", true);
        List<LearningPlanAiTaskResult> rawTasks = result.getTasks();
        if (rawTasks == null || rawTasks.size() < MIN_PLAN_TASKS || rawTasks.size() > MAX_PLAN_TASKS) {
            throw invalid("AI 返回的学习任务数量必须为2到6个");
        }

        Set<String> titles = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        List<LearningPlanAiTaskSuggestion> tasks = new ArrayList<>();
        LinkedHashMap<String, LearningAiEvidence> referencedEvidence = new LinkedHashMap<>();
        long totalPlannedMinutes = 0L;
        for (int index = 0; index < rawTasks.size(); index++) {
            LearningPlanAiTaskResult rawTask = rawTasks.get(index);
            if (rawTask == null) {
                throw invalid("AI 返回的第" + (index + 1) + "个任务为空");
            }
            String title = requiredText(rawTask.getTitle(), MAX_TASK_TITLE_LENGTH,
                    "第" + (index + 1) + "个任务标题");
            if (!titles.add(title.toLowerCase(Locale.ROOT))) {
                throw invalid("AI 返回了重复的学习任务标题");
            }
            String description = boundedText(rawTask.getDescription(), MAX_TASK_DESCRIPTION_LENGTH,
                    "第" + (index + 1) + "个任务描述", true);
            Integer plannedMinutes = rawTask.getPlannedMinutes();
            if (plannedMinutes == null || plannedMinutes <= 0) {
                throw invalid("AI 返回的第" + (index + 1) + "个任务计划用时必须大于0");
            }
            if (rawTask.getDueDate() == null
                    || rawTask.getDueDate().isBefore(context.request().getWeekStart())
                    || rawTask.getDueDate().isAfter(context.request().getWeekEnd())) {
                throw invalid("AI 返回的第" + (index + 1) + "个任务截止日期不在计划周期内");
            }
            Integer sortOrder = rawTask.getSortOrder();
            if (sortOrder == null || sortOrder < 0 || !sortOrders.add(sortOrder)) {
                throw invalid("AI 返回的任务排序值必须唯一且不能小于0");
            }
            List<LearningAiEvidence> evidence = resolveEvidence(
                    rawTask.getEvidenceKeys(), context.evidenceByKey(), referencedEvidence);
            if (evidence.isEmpty()) {
                throw invalid("AI 返回的第" + (index + 1) + "个任务缺少有效证据");
            }
            totalPlannedMinutes += plannedMinutes;
            if (totalPlannedMinutes > context.request().getAvailableMinutes()) {
                throw invalid("AI 返回任务的总计划用时超过每周可用时间");
            }
            tasks.add(new LearningPlanAiTaskSuggestion(
                    title,
                    description,
                    plannedMinutes,
                    rawTask.getDueDate(),
                    sortOrder,
                    evidence.stream().map(LearningAiEvidence::key).toList()));
        }

        List<String> warnings = mergeWarnings(context.warnings(), result.getWarnings());
        return new LearningPlanAiSuggestionResponse(
                context.request().getWeekStart(),
                context.request().getWeekEnd(),
                context.request().getAvailableMinutes(),
                mainGoal,
                rationale,
                List.copyOf(tasks),
                (int) totalPlannedMinutes,
                context.request().getAvailableMinutes() - (int) totalPlannedMinutes,
                List.copyOf(context.evidenceByKey().values()),
                warnings);
    }

    private LearningReviewAiSuggestionResponse validateReviewResult(
            LearningAiContextBuilder.ReviewContext context,
            LearningReviewAiResult result) {
        if (result == null) {
            throw invalid("AI 返回为空");
        }
        String summary = requiredText(result.getSummary(), MAX_REVIEW_TEXT_LENGTH, "summary");
        LinkedHashMap<String, LearningAiEvidence> referencedEvidence = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(mergeWarnings(context.warnings(), result.getWarnings()));
        List<LearningAiReviewItem> achievements = resolveReviewItems(
                result.getAchievements(), "achievements", context.evidenceByKey(), referencedEvidence, warnings);
        List<LearningAiReviewItem> problems = resolveReviewItems(
                result.getProblems(), "problems", context.evidenceByKey(), referencedEvidence, warnings);
        List<LearningAiReviewItem> nextSteps = resolveReviewItems(
                result.getNextSteps(), "nextSteps", context.evidenceByKey(), referencedEvidence, warnings);
        return new LearningReviewAiSuggestionResponse(
                context.plan().getId(),
                context.existingReview() != null,
                context.metrics(),
                summary,
                joinReviewItems(achievements),
                joinReviewItems(problems),
                joinReviewItems(nextSteps),
                List.copyOf(achievements),
                List.copyOf(problems),
                List.copyOf(nextSteps),
                List.copyOf(context.evidenceByKey().values()),
                List.copyOf(warnings));
    }

    private List<LearningAiReviewItem> resolveReviewItems(
            List<LearningAiReviewItemResult> rawItems,
            String field,
            Map<String, LearningAiEvidence> evidenceByKey,
            Map<String, LearningAiEvidence> referencedEvidence,
            List<String> warnings) {
        if (rawItems == null || rawItems.isEmpty()) {
            return List.of();
        }
        if (rawItems.size() > MAX_REVIEW_ITEMS) {
            throw invalid(field + " 返回条目过多");
        }
        List<LearningAiReviewItem> resolved = new ArrayList<>();
        for (int index = 0; index < rawItems.size(); index++) {
            LearningAiReviewItemResult rawItem = rawItems.get(index);
            if (rawItem == null) {
                throw invalid(field + " 的第" + (index + 1) + "项为空");
            }
            String text = requiredText(rawItem.getText(), MAX_REVIEW_TEXT_LENGTH,
                    field + " 的第" + (index + 1) + "项");
            List<LearningAiEvidence> evidence = resolveEvidence(
                    rawItem.getEvidenceKeys(), evidenceByKey, referencedEvidence);
            if (evidence.isEmpty()) {
                // Review items are advice, not persisted facts. Drop an item
                // with no trusted source instead of presenting invented support.
                warnings.add("已丢弃" + field + "中缺少有效证据的条目。");
                continue;
            }
            resolved.add(new LearningAiReviewItem(text, evidence.stream().map(LearningAiEvidence::key).toList()));
        }
        return resolved;
    }

    private List<LearningAiEvidence> resolveEvidence(
            List<String> rawKeys,
            Map<String, LearningAiEvidence> evidenceByKey,
            Map<String, LearningAiEvidence> referencedEvidence) {
        if (rawKeys == null || rawKeys.isEmpty() || evidenceByKey == null || evidenceByKey.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        List<LearningAiEvidence> resolved = new ArrayList<>();
        for (String rawKey : rawKeys) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            // Evidence keys are an exact allow-list token, not free-form ids.
            // Do not normalize whitespace into a trusted key.
            String key = rawKey;
            if (!keys.add(key)) {
                continue;
            }
            LearningAiEvidence evidence = evidenceByKey.get(key);
            if (evidence == null) {
                continue;
            }
            resolved.add(evidence);
            referencedEvidence.putIfAbsent(key, evidence);
        }
        return resolved;
    }

    private List<String> mergeWarnings(List<String> contextWarnings, List<String> aiWarnings) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (contextWarnings != null) {
            contextWarnings.forEach(value -> addWarning(warnings, value));
        }
        if (aiWarnings != null) {
            if (aiWarnings.size() > MAX_WARNINGS) {
                throw invalid("AI 返回的 warnings 过多");
            }
            aiWarnings.forEach(value -> addWarning(warnings, value));
        }
        return List.copyOf(warnings);
    }

    private String joinReviewItems(List<LearningAiReviewItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream().map(LearningAiReviewItem::text).filter(Objects::nonNull).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    private void addWarning(Set<String> warnings, String value) {
        if (value == null || value.isBlank()) {
            throw invalid("AI 返回的 warning 无效");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_WARNING_LENGTH) {
            throw invalid("AI 返回的 warning 过长");
        }
        if (warnings.size() < MAX_WARNINGS) {
            warnings.add(normalized);
        }
    }

    private String requiredText(String value, int maxLength, String field) {
        String normalized = boundedText(value, maxLength, field, false);
        if (normalized == null || normalized.isBlank()) {
            throw invalid("AI 返回的" + field + "不能为空");
        }
        return normalized;
    }

    private String boundedText(String value, int maxLength, String field, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return "";
            }
            throw invalid("AI 返回的" + field + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid("AI 返回的" + field + "过长");
        }
        return normalized;
    }

    private AiInvalidResponseException invalid(String message) {
        return new AiInvalidResponseException(message);
    }
}
