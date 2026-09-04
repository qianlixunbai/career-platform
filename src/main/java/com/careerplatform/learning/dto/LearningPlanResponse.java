package com.careerplatform.learning.dto;

import com.careerplatform.learning.enums.LearningPlanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LearningPlanResponse(Long id, LocalDate weekStart, LocalDate weekEnd, String mainGoal,
                                   LearningPlanStatus status, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
