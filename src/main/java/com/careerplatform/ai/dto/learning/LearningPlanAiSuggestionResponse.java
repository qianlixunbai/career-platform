package com.careerplatform.ai.dto.learning;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/** Validated ephemeral weekly-plan suggestion; this type never represents a persisted plan. */
public record LearningPlanAiSuggestionResponse(
        LocalDate weekStart,
        LocalDate weekEnd,
        int availableMinutes,
        String mainGoal,
        String rationale,
        List<LearningPlanAiTaskSuggestion> tasks,
        int totalPlannedMinutes,
        int bufferMinutes,
        List<LearningAiEvidence> evidence,
        List<String> warnings) {

    /** UI-friendly source projection; the evidence map remains the trust boundary. */
    @JsonProperty("sources")
    public List<LearningAiSource> getSources() {
        if (evidence == null) {
            return List.of();
        }
        return evidence.stream()
                .map(item -> new LearningAiSource(item.key(), item.type(), item.label(), item.excerpt(),
                        item.excerpt() == null || item.excerpt().isBlank() ? List.of() : List.of(item.excerpt())))
                .toList();
    }

    public List<LearningAiSource> sources() {
        return getSources();
    }

    @JsonProperty("facts")
    public List<LearningAiFact> getFacts() {
        return List.of();
    }

    public List<LearningAiFact> facts() {
        return getFacts();
    }
}
