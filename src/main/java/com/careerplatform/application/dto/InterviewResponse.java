package com.careerplatform.application.dto;

import com.careerplatform.application.enums.InterviewResult;
import com.careerplatform.application.enums.InterviewType;

import java.time.LocalDateTime;

public record InterviewResponse(
        Long id,
        Long applicationId,
        Integer roundNo,
        InterviewType type,
        String title,
        LocalDateTime scheduledAt,
        LocalDateTime occurredAt,
        InterviewResult result,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
