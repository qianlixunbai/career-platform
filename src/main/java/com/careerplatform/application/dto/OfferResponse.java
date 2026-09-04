package com.careerplatform.application.dto;

import com.careerplatform.application.enums.OfferStatus;

import java.time.LocalDateTime;

public record OfferResponse(
        Long id,
        Long applicationId,
        OfferStatus status,
        String positionTitle,
        String compensation,
        LocalDateTime expiresAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
