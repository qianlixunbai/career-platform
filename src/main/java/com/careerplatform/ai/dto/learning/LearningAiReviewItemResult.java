package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Untrusted review item emitted by the provider before evidence resolution. */
public class LearningAiReviewItemResult {
    private String text;
    private List<String> evidenceKeys;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getEvidenceKeys() {
        return evidenceKeys;
    }

    public void setEvidenceKeys(List<String> evidenceKeys) {
        this.evidenceKeys = evidenceKeys;
    }
}
