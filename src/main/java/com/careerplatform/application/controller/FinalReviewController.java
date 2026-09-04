package com.careerplatform.application.controller;

import com.careerplatform.application.dto.FinalReviewRequest;
import com.careerplatform.application.dto.FinalReviewResponse;
import com.careerplatform.application.entity.FinalReview;
import com.careerplatform.application.service.ApplicationService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/final-review")
public class FinalReviewController {

    private final ApplicationService applicationService;

    public FinalReviewController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public FinalReviewResponse get(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return toResponse(applicationService.getFinalReview(applicationId, currentUserId));
    }

    @PutMapping
    public FinalReviewResponse upsert(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody FinalReviewRequest request) {
        return toResponse(applicationService.upsertFinalReview(applicationId, currentUserId, request));
    }

    private FinalReviewResponse toResponse(FinalReview value) {
        return new FinalReviewResponse(value.getId(), value.getApplicationId(), value.getSummary(),
                value.getLessonsLearned(), value.getImprovements(), value.getRating(), value.getReviewedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
