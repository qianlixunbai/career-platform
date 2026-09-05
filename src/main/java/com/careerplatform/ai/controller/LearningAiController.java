package com.careerplatform.ai.controller;

import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionResponse;
import com.careerplatform.ai.dto.learning.LearningReviewAiSuggestionResponse;
import com.careerplatform.ai.dto.learning.LearningPlanAiConfirmRequest;
import com.careerplatform.ai.dto.learning.LearningPlanAiConfirmResponse;
import com.careerplatform.ai.dto.learning.LearningPlanAiConfirmTaskRequest;
import com.careerplatform.ai.service.LearningAiService;
import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningPlanResponse;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.dto.LearningTaskResponse;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.entity.LearningTask;
import com.careerplatform.learning.enums.LearningPlanStatus;
import com.careerplatform.learning.enums.LearningTaskStatus;
import com.careerplatform.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP boundary for ephemeral Learning AI suggestions and deterministic confirmation. */
@RestController
@RequestMapping("/api/v1/learning-plans")
public class LearningAiController {

    private final LearningAiService learningAiService;
    private final LearningService learningService;

    public LearningAiController(LearningAiService learningAiService, LearningService learningService) {
        this.learningAiService = learningAiService;
        this.learningService = learningService;
    }

    @PostMapping("/ai/plan-suggestion")
    public LearningPlanAiSuggestionResponse suggestPlan(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningPlanAiSuggestionRequest request) {
        return learningAiService.suggestPlan(currentUserId, request);
    }

    @PostMapping("/{planId}/ai/review-suggestion")
    public LearningReviewAiSuggestionResponse suggestReview(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId) {
        return learningAiService.suggestReview(planId, currentUserId);
    }

    @PostMapping("/ai/plan-suggestion/confirm")
    public ResponseEntity<LearningPlanAiConfirmResponse> confirmPlan(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningPlanAiConfirmRequest request) {
        LearningPlanRequest planRequest = new LearningPlanRequest();
        planRequest.setWeekStart(request.getWeekStart());
        planRequest.setWeekEnd(request.getWeekEnd());
        planRequest.setMainGoal(request.getMainGoal().trim());
        planRequest.setStatus(LearningPlanStatus.PLANNED);

        List<LearningTaskRequest> taskRequests = request.getTasks().stream()
                .map(this::toTaskRequest)
                .toList();
        LearningService.CreatedPlanWithTasks created = learningService.createPlanWithTasks(
                currentUserId, planRequest, taskRequests, request.getAvailableMinutes());
        int totalPlannedMinutes = created.tasks().stream()
                .map(LearningTask::getPlannedMinutes)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return ResponseEntity.status(HttpStatus.CREATED).body(new LearningPlanAiConfirmResponse(
                toPlanResponse(created.plan()),
                created.tasks().stream().map(this::toTaskResponse).toList(),
                totalPlannedMinutes));
    }

    private LearningTaskRequest toTaskRequest(LearningPlanAiConfirmTaskRequest source) {
        LearningTaskRequest target = new LearningTaskRequest();
        target.setTitle(source.getTitle().trim());
        target.setDescription(source.getDescription() == null ? null : source.getDescription().trim());
        target.setStatus(LearningTaskStatus.TODO);
        target.setPlannedMinutes(source.getPlannedMinutes());
        target.setDueDate(source.getDueDate());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private LearningPlanResponse toPlanResponse(LearningPlan value) {
        return new LearningPlanResponse(value.getId(), value.getWeekStart(), value.getWeekEnd(),
                value.getMainGoal(), value.getStatus(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private LearningTaskResponse toTaskResponse(LearningTask value) {
        return new LearningTaskResponse(value.getId(), value.getTitle(), value.getDescription(), value.getStatus(),
                value.getPlannedMinutes(), value.getDueDate(), value.getSortOrder(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
