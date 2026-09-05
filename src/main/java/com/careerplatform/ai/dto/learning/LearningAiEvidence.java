package com.careerplatform.ai.dto.learning;

/**
 * A trusted fact returned from the server-side context map.
 *
 * <p>The model only returns a source key.  The text in this type is always
 * copied from the context that Java built for the current user and request.</p>
 */
public record LearningAiEvidence(
        String key,
        LearningAiEvidenceType type,
        String label,
        String excerpt) {

    /** Convenience alias for server-side code and callers using sourceKey terminology. */
    public String sourceKey() {
        return key;
    }
}
