package com.careerplatform.ai.dto;

import java.util.List;

public record JdParseResponse(
        String sourceFingerprint,
        List<JdRequirementCandidateResponse> requirements,
        List<String> warnings) {
}
