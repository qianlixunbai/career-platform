package com.careerplatform.learning.controller;

import com.careerplatform.auth.CurrentUserId;
import com.careerplatform.learning.dto.LearningTaskRequest;
import com.careerplatform.learning.dto.LearningTaskResponse;
import com.careerplatform.learning.entity.LearningTask;
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
@RequestMapping("/api/v1/learning-plans/{planId}/tasks")
public class LearningTaskController {

    private final LearningService learningService;

    public LearningTaskController(LearningService learningService) {
        this.learningService = learningService;
    }

    @PostMapping
    public ResponseEntity<LearningTaskResponse> create(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(learningService.createTask(planId, currentUserId, request)));
    }

    @GetMapping
    public List<LearningTaskResponse> list(
            @PathVariable Long planId,
            @CurrentUserId Long currentUserId) {
        return learningService.listTasks(planId, currentUserId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{taskId}")
    public LearningTaskResponse get(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @CurrentUserId Long currentUserId) {
        return toResponse(learningService.getTask(planId, taskId, currentUserId));
    }

    @PutMapping("/{taskId}")
    public LearningTaskResponse update(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody LearningTaskRequest request) {
        return toResponse(learningService.updateTask(planId, taskId, currentUserId, request));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long planId,
            @PathVariable Long taskId,
            @CurrentUserId Long currentUserId) {
        learningService.deleteTask(planId, taskId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    private LearningTaskResponse toResponse(LearningTask value) {
        return new LearningTaskResponse(value.getId(), value.getTitle(), value.getDescription(), value.getStatus(),
                value.getPlannedMinutes(), value.getDueDate(), value.getSortOrder(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
