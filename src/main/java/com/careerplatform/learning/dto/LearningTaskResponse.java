package com.careerplatform.learning.dto;

import com.careerplatform.learning.enums.LearningTaskStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LearningTaskResponse(Long id, String title, String description, LearningTaskStatus status,
                                   Integer plannedMinutes, LocalDate dueDate, Integer sortOrder,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
