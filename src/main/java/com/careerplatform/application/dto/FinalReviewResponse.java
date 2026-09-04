package com.careerplatform.application.dto;

import java.time.LocalDateTime;

public record FinalReviewResponse(
        Long id,
        Long applicationId,
        String summary,
        String lessonsLearned,
        String improvements,
        Integer rating,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
