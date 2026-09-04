package com.careerplatform.application.dto;

import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;

import java.time.LocalDateTime;

public record ApplicationResponse(
        Long id,
        Long jobId,
        Long resumeVersionId,
        ApplicationStage currentStage,
        ApplicationEndReason endReason,
        String endNote,
        String jobTitleSnapshot,
        String companyNameSnapshot,
        String locationSnapshot,
        String jobDescriptionSnapshot,
        LocalDateTime appliedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
