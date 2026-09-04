package com.careerplatform.ai.controller;

import com.careerplatform.ai.dto.JdParseConfirmRequest;
import com.careerplatform.ai.dto.JdParseConfirmResponse;
import com.careerplatform.ai.dto.JdParseResponse;
import com.careerplatform.ai.service.JdParseService;
import com.careerplatform.auth.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs/{jobId}/ai/jd-parse")
public class JdAiController {
    private final JdParseService jdParseService;

    public JdAiController(JdParseService jdParseService) {
        this.jdParseService = jdParseService;
    }

    @PostMapping
    public JdParseResponse parse(@PathVariable Long jobId, @CurrentUserId Long currentUserId) {
        return jdParseService.parse(jobId, currentUserId);
    }

    @PostMapping("/confirm")
    public ResponseEntity<JdParseConfirmResponse> confirm(
            @PathVariable Long jobId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody JdParseConfirmRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jdParseService.confirm(jobId, currentUserId, request));
    }
}
