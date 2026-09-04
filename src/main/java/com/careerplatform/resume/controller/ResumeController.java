package com.careerplatform.resume.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.resume.dto.ResumeRequest;
import com.careerplatform.resume.dto.ResumeResponse;
import com.careerplatform.resume.entity.Resume;
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
@RequestMapping("/api/v1/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ResponseEntity<ResumeResponse> create(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(resumeService.createResume(currentUserId, request)));
    }

    @GetMapping
    public List<ResumeResponse> list(@CurrentUserId Long currentUserId) {
        return resumeService.listResumes(currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{resumeId}")
    public ResumeResponse get(@PathVariable Long resumeId, @CurrentUserId Long currentUserId) {
        return toResponse(resumeService.getResume(resumeId, currentUserId));
    }

    @PutMapping("/{resumeId}")
    public ResumeResponse update(
            @PathVariable Long resumeId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeRequest request) {
        return toResponse(resumeService.updateResume(resumeId, currentUserId, request));
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(@PathVariable Long resumeId, @CurrentUserId Long currentUserId) {
        resumeService.deleteResume(resumeId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private ResumeResponse toResponse(Resume value) {
        return new ResumeResponse(value.getId(), value.getName(), value.getDescription(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
