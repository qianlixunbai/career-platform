package com.careerplatform.resume.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.resume.dto.ResumeVersionRequest;
import com.careerplatform.resume.dto.ResumeVersionResponse;
import com.careerplatform.resume.entity.ResumeVersion;
import com.careerplatform.resume.service.ResumeService;
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
@RequestMapping("/api/v1/resumes/{resumeId}/versions")
public class ResumeVersionController {

    private final ResumeService resumeService;

    public ResumeVersionController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ResponseEntity<ResumeVersionResponse> create(
            @PathVariable Long resumeId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(resumeService.createVersion(resumeId, currentUserId, request)));
    }

    @PostMapping("/generate")
    public ResponseEntity<ResumeVersionResponse> generate(
            @PathVariable Long resumeId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(resumeService.generateVersion(resumeId, currentUserId, request)));
    }

    @GetMapping
    public List<ResumeVersionResponse> list(
            @PathVariable Long resumeId,
            @CurrentUserId Long currentUserId) {
        return resumeService.listVersions(resumeId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{versionId}")
    public ResumeVersionResponse get(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        return toResponse(resumeService.getVersion(resumeId, versionId, currentUserId));
    }

    @PutMapping("/{versionId}")
    public ResumeVersionResponse update(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeVersionRequest request) {
        return toResponse(resumeService.updateVersion(resumeId, versionId, currentUserId, request));
    }

    @PostMapping("/{versionId}/copy")
    public ResponseEntity<ResumeVersionResponse> copy(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(resumeService.copyVersion(resumeId, versionId, currentUserId, request)));
    }

    @PostMapping("/{versionId}/finalize")
    public ResumeVersionResponse finalizeVersion(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        return toResponse(resumeService.finalizeVersion(resumeId, versionId, currentUserId));
    }

    @DeleteMapping("/{versionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        resumeService.deleteVersion(resumeId, versionId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private ResumeVersionResponse toResponse(ResumeVersion value) {
        return new ResumeVersionResponse(value.getId(), value.getResumeId(), value.getVersionNo(), value.getLabel(),
                value.getStatus(), value.getFinalizedAt(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
