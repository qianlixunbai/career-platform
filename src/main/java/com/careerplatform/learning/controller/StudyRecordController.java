package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.StudyRecordRequest;
import com.careerplatform.learning.dto.StudyRecordResponse;
import com.careerplatform.learning.entity.StudyRecord;
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
@RequestMapping("/api/v1/learning-tasks/{taskId}/records")
public class StudyRecordController {

    private final LearningService learningService;

    public StudyRecordController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public ResponseEntity<StudyRecordResponse> create(
            @PathVariable Long taskId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody StudyRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createRecord(taskId, currentUserId, request)));
    }

    @GetMapping
    public List<StudyRecordResponse> list(
            @PathVariable Long taskId,
            @CurrentUserId Long currentUserId) {
        return learningService.listRecords(taskId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{recordId}")
    public StudyRecordResponse get(
            @PathVariable Long taskId,
            @PathVariable Long recordId,
            @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getRecord(taskId, recordId, currentUserId));
    }

    @PutMapping("/{recordId}")
    public StudyRecordResponse update(
            @PathVariable Long taskId,
            @PathVariable Long recordId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody StudyRecordRequest request) {
        return toResponse(learningService.updateRecord(taskId, recordId, currentUserId, request));
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long taskId,
            @PathVariable Long recordId,
            @CurrentUserId Long currentUserId) {
        learningService.deleteRecord(taskId, recordId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private StudyRecordResponse toResponse(StudyRecord value) {
        return new StudyRecordResponse(value.getId(), value.getStudiedAt(), value.getDurationMinutes(),
                value.getContent(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
