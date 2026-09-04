package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningNoteRequest;
import com.careerplatform.learning.dto.LearningNoteResponse;
import com.careerplatform.learning.entity.LearningNote;
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
@RequestMapping("/api/v1/learning-plans/{planId}/notes")
public class LearningNoteController {

    private final LearningService learningService;

    public LearningNoteController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public ResponseEntity<LearningNoteResponse> create(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createNote(planId, currentUserId, request)));
    }

    @GetMapping
    public List<LearningNoteResponse> list(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId) {
        return learningService.listNotes(planId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{noteId}")
    public LearningNoteResponse get(
            @PathVariable Long planId,
            @PathVariable Long noteId,
            @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getNote(planId, noteId, currentUserId));
    }

    @PutMapping("/{noteId}")
    public LearningNoteResponse update(
            @PathVariable Long planId,
            @PathVariable Long noteId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningNoteRequest request) {
        return toResponse(learningService.updateNote(planId, noteId, currentUserId, request));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long planId,
            @PathVariable Long noteId,
            @CurrentUserId Long currentUserId) {
        learningService.deleteNote(planId, noteId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private LearningNoteResponse toResponse(LearningNote value) {
        return new LearningNoteResponse(value.getId(), value.getPlanId(), value.getTaskId(), value.getTitle(),
                value.getContent(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
