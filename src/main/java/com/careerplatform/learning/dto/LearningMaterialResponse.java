package com.careerplatform.learning.dto;

import java.time.LocalDateTime;

public record LearningMaterialResponse(Long id, Long planId, Long taskId, String title,
                                       String sourceUrl, String description, LocalDateTime createdAt,
                                       LocalDateTime updatedAt) {
}
