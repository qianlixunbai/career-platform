package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.WeeklyReviewRequest;
import com.careerplatform.learning.dto.WeeklyReviewResponse;
import com.careerplatform.learning.entity.WeeklyReview;
import com.careerplatform.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-plans/{planId}/review")
public class WeeklyReviewController {

    private final LearningService learningService;

    public WeeklyReviewController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping
    public WeeklyReviewResponse get(@PathVariable Long planId, @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getReview(planId, currentUserId));
    }

    @PutMapping
    public WeeklyReviewResponse upsert(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody WeeklyReviewRequest request) {
        return toResponse(learningService.upsertReview(planId, currentUserId, request));
    }

    private WeeklyReviewResponse toResponse(WeeklyReview value) {
        return new WeeklyReviewResponse(value.getId(), value.getPlanId(), value.getSummary(),
                value.getAchievements(), value.getProblems(), value.getNextSteps(), value.getReviewedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
