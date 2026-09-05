package com.careerplatform.ai.dto.learning;

/** A small trusted fact projection; values are always Java-owned context text. */
public record LearningAiFact(
        String key,
        String label,
        String value,
        String sourceKey) {
}
