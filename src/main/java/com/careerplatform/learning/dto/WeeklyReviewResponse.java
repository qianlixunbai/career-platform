package com.careerplatform.learning.dto;

import java.time.LocalDateTime;

public record WeeklyReviewResponse(Long id, Long planId, String summary, String achievements,
                                   String problems, String nextSteps, LocalDateTime reviewedAt,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
}
