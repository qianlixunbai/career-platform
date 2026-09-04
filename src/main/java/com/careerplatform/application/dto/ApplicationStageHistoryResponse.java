package com.careerplatform.application.dto;

import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;

import java.time.LocalDateTime;

public record ApplicationStageHistoryResponse(
        Long id,
        Long applicationId,
        ApplicationStage fromStage,
        ApplicationStage toStage,
        ApplicationEndReason endReason,
        String note,
        LocalDateTime changedAt,
        LocalDateTime createdAt) {

    public ApplicationStageHistoryResponse(Long id,
                                           ApplicationStage fromStage,
                                           ApplicationStage toStage,
                                           ApplicationEndReason endReason,
                                           String note,
                                           LocalDateTime changedAt) {
        this(id, null, fromStage, toStage, endReason, note, changedAt, null);
    }
}
