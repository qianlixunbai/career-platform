package com.careerplatform.learning.dto;

import java.time.LocalDateTime;

public record LearningNoteResponse(Long id, Long planId, Long taskId, String title, String content,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
