package com.careerplatform.ai.service;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.dto.learning.LearningAiEvidence;
import com.careerplatform.ai.dto.learning.LearningAiEvidenceType;
import com.careerplatform.ai.dto.learning.LearningAiReviewItemResult;
import com.careerplatform.ai.dto.learning.LearningPlanAiResult;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import com.careerplatform.ai.dto.learning.LearningPlanAiTaskResult;
import com.careerplatform.ai.dto.learning.LearningReviewAiResult;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.learning.entity.LearningPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningAiServiceTest {
    private static final Long USER_ID = 19L;
    private static final Long PLAN_ID = 27L;

    @Mock
    private AiChatGateway gateway;
    @Mock
    private LearningAiContextBuilder contextBuilder;

    private LearningAiService service;
    private LearningPlanAiSuggestionRequest request;
    private LearningAiContextBuilder.PlanContext planContext;
    private LearningAiEvidence focusEvidence;

    @BeforeEach
    void setUp() {
        service = new LearningAiService(gateway, contextBuilder, new LearningAiPromptFactory());
        request = new LearningPlanAiSuggestionRequest();
        request.setWeekStart(LocalDate.of(2026, 9, 7));
        request.setWeekEnd(LocalDate.of(2026, 9, 13));
        request.setAvailableMinutes(180);
        focusEvidence = new LearningAiEvidence(
                "USER_FOCUS:CURRENT", LearningAiEvidenceType.USER_FOCUS, "本周关注", "Redis 基础");
        Map<String, LearningAiEvidence> evidence = new LinkedHashMap<>();
        evidence.put(focusEvidence.key(), focusEvidence);
        planContext = new LearningAiContextBuilder.PlanContext(
                request, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                evidence, List.of(), 20);
        lenient().when(contextBuilder.buildPlanContext(USER_ID, request)).thenReturn(planContext);
    }

    @Test
    void validatesPlanCandidateAndRecomputesTotalsFromTasks() {
        when(gateway.generateStructured(any(), any(), eq(LearningPlanAiResult.class)))
                .thenReturn(planResult(task("Redis drills", 60, 0), task("Redis recap", 45, 1)));

        var response = service.suggestPlan(USER_ID, request);

        assertThat(response.totalPlannedMinutes()).isEqualTo(105);
        assertThat(response.bufferMinutes()).isEqualTo(75);
        assertThat(response.tasks()).extracting(task -> task.evidenceKeys())
                .containsExactly(List.of("USER_FOCUS:CURRENT"), List.of("USER_FOCUS:CURRENT"));
        assertThat(response.evidence()).containsExactly(focusEvidence);
        verify(gateway).generateStructured(
                eq(new LearningAiPromptFactory().systemInstruction()), any(), eq(LearningPlanAiResult.class));
    }

    @Test
    void unknownEvidenceIsDroppedAndTaskWithoutTrustedEvidenceIsRejected() {
        LearningPlanAiTaskResult task = task("Redis drills", 60, 0);
        task.setEvidenceKeys(List.of("UNKNOWN:999"));
        when(gateway.generateStructured(any(), any(), eq(LearningPlanAiResult.class)))
                .thenReturn(planResult(task, task("Redis recap", 45, 1)));

        assertThatThrownBy(() -> service.suggestPlan(USER_ID, request))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("缺少有效证据");
    }

    @Test
    void duplicateTitleDueDateAndBudgetAreRejected() {
        LearningPlanAiTaskResult duplicate = task("Redis drills", 60, 0);
        when(gateway.generateStructured(any(), any(), eq(LearningPlanAiResult.class)))
                .thenReturn(planResult(duplicate, task(" redis drills ", 45, 1)));
        assertThatThrownBy(() -> service.suggestPlan(USER_ID, request))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("重复");

        LearningPlanAiTaskResult outside = task("Redis outside", 60, 0);
        outside.setDueDate(request.getWeekEnd().plusDays(1));
        when(gateway.generateStructured(any(), any(), eq(LearningPlanAiResult.class)))
                .thenReturn(planResult(outside, task("Redis recap", 45, 1)));
        assertThatThrownBy(() -> service.suggestPlan(USER_ID, request))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("计划周期");

        when(gateway.generateStructured(any(), any(), eq(LearningPlanAiResult.class)))
                .thenReturn(planResult(task("Redis 1", 120, 0), task("Redis 2", 61, 1)));
        assertThatThrownBy(() -> service.suggestPlan(USER_ID, request))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("总计划用时");
    }

    @Test
    void reviewUsesOwnerCheckedContextMetricsAndDoesNotPersist() {
        LearningPlan plan = new LearningPlan();
        plan.setId(PLAN_ID);
        LearningAiReviewItemResult achievement = reviewItem("完成了 Redis 练习", "USER_FOCUS:CURRENT");
        LearningReviewAiResult result = new LearningReviewAiResult();
        result.setSummary("本周有明确投入。");
        result.setAchievements(List.of(achievement));
        result.setProblems(List.of());
        result.setNextSteps(List.of());
        result.setWarnings(List.of());

        LearningAiContextBuilder.ReviewContext context = new LearningAiContextBuilder.ReviewContext(
                plan, List.of(), List.of(), List.of(), null,
                new com.careerplatform.ai.dto.learning.LearningAiReviewMetrics(
                        0, 0, 0, 0, 0, java.math.BigDecimal.ZERO.setScale(2), 0, 0, 0,
                        Map.of(), List.of()),
                Map.of(focusEvidence.key(), focusEvidence), List.of(), 20);
        when(contextBuilder.buildReviewContext(USER_ID, PLAN_ID)).thenReturn(context);
        when(gateway.generateStructured(any(), any(), eq(LearningReviewAiResult.class))).thenReturn(result);

        var response = service.suggestReview(PLAN_ID, USER_ID);

        assertThat(response.planId()).isEqualTo(PLAN_ID);
        assertThat(response.hasExistingReview()).isFalse();
        assertThat(response.achievementItems()).hasSize(1);
        assertThat(response.achievementItems().getFirst().evidenceKeys())
                .containsExactly("USER_FOCUS:CURRENT");
        assertThat(response.achievements()).contains("完成了 Redis 练习");
    }

    private LearningPlanAiResult planResult(LearningPlanAiTaskResult first, LearningPlanAiTaskResult second) {
        LearningPlanAiResult result = new LearningPlanAiResult();
        result.setMainGoal("完成 Redis 基础练习");
        result.setRationale("按可用时间安排重点练习。");
        result.setTasks(List.of(first, second));
        result.setWarnings(List.of());
        return result;
    }

    private LearningPlanAiTaskResult task(String title, int minutes, int sortOrder) {
        LearningPlanAiTaskResult result = new LearningPlanAiTaskResult();
        result.setTitle(title);
        result.setDescription("完成可验证的小练习并记录结果");
        result.setPlannedMinutes(minutes);
        result.setDueDate(request.getWeekStart().plusDays(sortOrder));
        result.setSortOrder(sortOrder);
        result.setEvidenceKeys(List.of("USER_FOCUS:CURRENT"));
        return result;
    }

    private LearningAiReviewItemResult reviewItem(String text, String key) {
        LearningAiReviewItemResult result = new LearningAiReviewItemResult();
        result.setText(text);
        result.setEvidenceKeys(List.of(key));
        return result;
    }
}
