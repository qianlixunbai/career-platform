package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningMaterialRequest;
import com.careerplatform.learning.dto.LearningMaterialResponse;
import com.careerplatform.learning.entity.LearningMaterial;
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
@RequestMapping("/api/v1/learning-plans/{planId}/materials")
public class LearningMaterialController {

    private final LearningService learningService;

    public LearningMaterialController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public ResponseEntity<LearningMaterialResponse> create(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createMaterial(planId, currentUserId, request)));
    }

    @GetMapping
    public List<LearningMaterialResponse> list(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId) {
        return learningService.listMaterials(planId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{materialId}")
    public LearningMaterialResponse get(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getMaterial(planId, materialId, currentUserId));
    }

    @PutMapping("/{materialId}")
    public LearningMaterialResponse update(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningMaterialRequest request) {
        return toResponse(learningService.updateMaterial(planId, materialId, currentUserId, request));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long planId,
            @PathVariable Long materialId,
            @CurrentUserId Long currentUserId) {
        learningService.deleteMaterial(planId, materialId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private LearningMaterialResponse toResponse(LearningMaterial value) {
        return new LearningMaterialResponse(value.getId(), value.getPlanId(), value.getTaskId(), value.getTitle(),
                value.getSourceUrl(), value.getDescription(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
