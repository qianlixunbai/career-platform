package com.careerplatform.ai.controller;

import com.careerplatform.ai.dto.job.JobDiscoveryConfirmRequest;
import com.careerplatform.ai.dto.job.JobDiscoveryConfirmResponse;
import com.careerplatform.ai.dto.job.JobDiscoveryRequest;
import com.careerplatform.ai.dto.job.JobDiscoveryResponse;
import com.careerplatform.ai.service.JobDiscoveryService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for ephemeral job discovery and explicit confirmation. */
@RestController
@RequestMapping("/api/v1/jobs/ai/discovery")
public class JobDiscoveryController {

    private final JobDiscoveryService jobDiscoveryService;

    public JobDiscoveryController(JobDiscoveryService jobDiscoveryService) {
        this.jobDiscoveryService = jobDiscoveryService;
    }

    @PostMapping
    public JobDiscoveryResponse discover(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody JobDiscoveryRequest request) {
        return jobDiscoveryService.discover(currentUserId, request);
    }

    @PostMapping("/confirm")
    public ResponseEntity<JobDiscoveryConfirmResponse> confirm(
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody JobDiscoveryConfirmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(jobDiscoveryService.confirm(currentUserId, request));
    }
}
