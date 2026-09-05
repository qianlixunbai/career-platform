package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Compact trusted source projection used by the Learning UI. */
public record LearningAiSource(
        String key,
        LearningAiEvidenceType type,
        String label,
        String excerpt,
        List<String> facts) {
}
