package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Validated review advice item whose sources were resolved by Java. */
public record LearningAiReviewItem(String text, List<String> evidenceKeys) {

    public List<String> evidence() {
        return evidenceKeys;
    }
}
