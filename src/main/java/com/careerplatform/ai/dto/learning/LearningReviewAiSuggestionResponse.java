package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Validated ephemeral weekly-review suggestion; existing reviews are never overwritten. */
public record LearningReviewAiSuggestionResponse(
        Long planId,
        boolean hasExistingReview,
        LearningAiReviewMetrics metrics,
        String summary,
        String achievements,
        String problems,
        String nextSteps,
        List<LearningAiReviewItem> achievementItems,
        List<LearningAiReviewItem> problemItems,
        List<LearningAiReviewItem> nextStepItems,
        List<LearningAiEvidence> evidence,
        List<String> warnings) {

    /** Server-side terminology alias retained for Java callers. */
    public boolean existingReviewPresent() {
        return hasExistingReview;
    }
}
