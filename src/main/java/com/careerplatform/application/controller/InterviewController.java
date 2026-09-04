package com.careerplatform.application.controller;

import com.careerplatform.application.dto.InterviewRequest;
import com.careerplatform.application.dto.InterviewResponse;
import com.careerplatform.application.entity.Interview;
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
@RequestMapping("/api/v1/applications/{applicationId}/interviews")
public class InterviewController {

    private final ApplicationService applicationService;

    public InterviewController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<InterviewResponse> create(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody InterviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(applicationService.createInterview(applicationId, currentUserId, request)));
    }

    @GetMapping
    public List<InterviewResponse> list(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return applicationService.listInterviews(applicationId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{interviewId}")
    public InterviewResponse get(
            @PathVariable Long applicationId,
            @PathVariable Long interviewId,
            @CurrentUserId Long currentUserId) {
        return toResponse(applicationService.getInterview(applicationId, interviewId, currentUserId));
    }

    @PutMapping("/{interviewId}")
    public InterviewResponse update(
            @PathVariable Long applicationId,
            @PathVariable Long interviewId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody InterviewRequest request) {
        return toResponse(applicationService.updateInterview(applicationId, interviewId, currentUserId, request));
    }

    @DeleteMapping("/{interviewId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long applicationId,
            @PathVariable Long interviewId,
            @CurrentUserId Long currentUserId) {
        applicationService.deleteInterview(applicationId, interviewId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private InterviewResponse toResponse(Interview value) {
        return new InterviewResponse(value.getId(), value.getApplicationId(), value.getRoundNo(), value.getType(),
                value.getTitle(), value.getScheduledAt(), value.getOccurredAt(), value.getResult(), value.getNotes(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
