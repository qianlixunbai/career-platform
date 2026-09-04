package com.careerplatform.application.controller;

import com.careerplatform.application.dto.ApplicationResponse;
import com.careerplatform.application.dto.ApplicationStageHistoryResponse;
import com.careerplatform.application.dto.ApplicationTransitionRequest;
import com.careerplatform.application.dto.CreateApplicationRequest;
import com.careerplatform.application.entity.Application;
import com.careerplatform.application.entity.ApplicationStageHistory;
import com.careerplatform.application.enums.ApplicationStage;
import com.careerplatform.application.service.ApplicationService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(applicationService.createApplication(currentUserId, request)));
    }

    @GetMapping
    public List<ApplicationResponse> list(
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) ApplicationStage currentStage,
            @RequestParam(required = false) Long jobId) {
        return applicationService.listApplications(currentUserId, currentStage, jobId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse get(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return toResponse(applicationService.getApplication(applicationId, currentUserId));
    }

    @PostMapping("/{applicationId}/transitions")
    public ApplicationResponse transition(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ApplicationTransitionRequest request) {
        return toResponse(applicationService.transition(applicationId, currentUserId, request));
    }

    @GetMapping("/{applicationId}/history")
    public List<ApplicationStageHistoryResponse> history(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return applicationService.listHistory(applicationId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ApplicationResponse toResponse(Application value) {
        return new ApplicationResponse(value.getId(), value.getJobId(), value.getResumeVersionId(),
                value.getCurrentStage(), value.getEndReason(), value.getEndNote(),
                value.getJobTitleSnapshot(), value.getCompanyNameSnapshot(), value.getLocationSnapshot(),
                value.getJobDescriptionSnapshot(), value.getAppliedAt(), value.getEndedAt(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private ApplicationStageHistoryResponse toResponse(ApplicationStageHistory value) {
        return new ApplicationStageHistoryResponse(value.getId(), value.getApplicationId(), value.getFromStage(),
                value.getToStage(), value.getEndReason(), value.getNote(), value.getChangedAt(),
                value.getCreatedAt());
    }
}
