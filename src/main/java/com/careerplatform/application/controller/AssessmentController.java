package com.careerplatform.application.controller;

import com.careerplatform.application.dto.AssessmentRequest;
import com.careerplatform.application.dto.AssessmentResponse;
import com.careerplatform.application.entity.Assessment;
import com.careerplatform.application.service.ApplicationService;
import com.careerplatform.auth.CurrentUserId;
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
@RequestMapping("/api/v1/applications/{applicationId}/assessments")
public class AssessmentController {

    private final ApplicationService applicationService;

    public AssessmentController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<AssessmentResponse> create(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody AssessmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(applicationService.createAssessment(applicationId, currentUserId, request)));
    }

    @GetMapping
    public List<AssessmentResponse> list(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return applicationService.listAssessments(applicationId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{assessmentId}")
    public AssessmentResponse get(
            @PathVariable Long applicationId,
            @PathVariable Long assessmentId,
            @CurrentUserId Long currentUserId) {
        return toResponse(applicationService.getAssessment(applicationId, assessmentId, currentUserId));
    }

    @PutMapping("/{assessmentId}")
    public AssessmentResponse update(
            @PathVariable Long applicationId,
            @PathVariable Long assessmentId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody AssessmentRequest request) {
        return toResponse(applicationService.updateAssessment(applicationId, assessmentId, currentUserId, request));
    }

    @DeleteMapping("/{assessmentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long applicationId,
            @PathVariable Long assessmentId,
            @CurrentUserId Long currentUserId) {
        applicationService.deleteAssessment(applicationId, assessmentId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private AssessmentResponse toResponse(Assessment value) {
        return new AssessmentResponse(value.getId(), value.getApplicationId(), value.getType(), value.getTitle(),
                value.getScheduledAt(), value.getOccurredAt(), value.getResult(), value.getNotes(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
