package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningPlanRequest;
import com.careerplatform.learning.dto.LearningPlanResponse;
import com.careerplatform.learning.entity.LearningPlan;
import com.careerplatform.learning.service.LearningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learning-plans")
public class LearningPlanController {

    private final LearningService learningService;

    public LearningPlanController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public ResponseEntity<LearningPlanResponse> create(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createPlan(currentUserId, request)));
    }

    @GetMapping
    public List<LearningPlanResponse> list(@CurrentUserId Long currentUserId) {
        return learningService.listPlans(currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{planId}")
    public LearningPlanResponse get(@PathVariable Long planId, @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getPlan(planId, currentUserId));
    }

    @PutMapping("/{planId}")
    public LearningPlanResponse update(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningPlanRequest request) {
        return toResponse(learningService.updatePlan(planId, currentUserId, request));
    }

    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> delete(@PathVariable Long planId, @CurrentUserId Long currentUserId) {
        learningService.deletePlan(planId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private LearningPlanResponse toResponse(LearningPlan value) {
        return new LearningPlanResponse(value.getId(), value.getWeekStart(), value.getWeekEnd(),
                value.getMainGoal(), value.getStatus(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
