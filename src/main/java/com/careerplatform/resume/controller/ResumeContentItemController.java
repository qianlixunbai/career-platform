package com.careerplatform.resume.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.resume.dto.ResumeContentItemRequest;
import com.careerplatform.resume.dto.ResumeContentItemResponse;
import com.careerplatform.resume.entity.ResumeContentItem;
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
@RequestMapping("/api/v1/resumes/{resumeId}/versions/{versionId}/items")
public class ResumeContentItemController {

    private final ResumeService resumeService;

    public ResumeContentItemController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ResponseEntity<ResumeContentItemResponse> create(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeContentItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(resumeService.createItem(resumeId, versionId, currentUserId, request)));
    }

    @GetMapping
    public List<ResumeContentItemResponse> list(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @CurrentUserId Long currentUserId) {
        return resumeService.listItems(resumeId, versionId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{itemId}")
    public ResumeContentItemResponse get(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @PathVariable Long itemId,
            @CurrentUserId Long currentUserId) {
        return toResponse(resumeService.getItem(resumeId, versionId, itemId, currentUserId));
    }

    @PutMapping("/{itemId}")
    public ResumeContentItemResponse update(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @PathVariable Long itemId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ResumeContentItemRequest request) {
        return toResponse(resumeService.updateItem(resumeId, versionId, itemId, currentUserId, request));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long resumeId,
            @PathVariable Long versionId,
            @PathVariable Long itemId,
            @CurrentUserId Long currentUserId) {
        resumeService.deleteItem(resumeId, versionId, itemId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private ResumeContentItemResponse toResponse(ResumeContentItem value) {
        return new ResumeContentItemResponse(value.getId(), value.getVersionId(), value.getSectionType(),
                value.getTitle(), value.getContent(), value.getSourceType(), value.getSourceId(),
                value.getSortOrder(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
