package com.careerplatform.application.dto;

import com.careerplatform.application.enums.AssessmentResult;
import com.careerplatform.application.enums.AssessmentType;

import java.time.LocalDateTime;

public record AssessmentResponse(
        Long id,
        Long applicationId,
        AssessmentType type,
        String title,
        LocalDateTime scheduledAt,
        LocalDateTime occurredAt,
        AssessmentResult result,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
