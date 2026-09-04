package com.careerplatform.application.controller;

import com.careerplatform.application.dto.OfferCreateRequest;
import com.careerplatform.application.dto.OfferResponse;
import com.careerplatform.application.dto.OfferUpdateRequest;
import com.careerplatform.application.entity.Offer;
import com.careerplatform.application.service.ApplicationService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications/{applicationId}/offer")
public class OfferController {

    private final ApplicationService applicationService;

    public OfferController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<OfferResponse> create(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody OfferCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(applicationService.createOffer(applicationId, currentUserId, request)));
    }

    @GetMapping
    public OfferResponse get(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId) {
        return toResponse(applicationService.getOffer(applicationId, currentUserId));
    }

    @PutMapping
    public OfferResponse update(
            @PathVariable Long applicationId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody OfferUpdateRequest request) {
        return toResponse(applicationService.updateOffer(applicationId, currentUserId, request));
    }

    private OfferResponse toResponse(Offer value) {
        return new OfferResponse(value.getId(), value.getApplicationId(), value.getStatus(), value.getPositionTitle(),
                value.getCompensation(), value.getExpiresAt(), value.getNotes(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
