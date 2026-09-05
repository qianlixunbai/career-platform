package com.careerplatform.ai.dto.learning;

import java.time.LocalDate;
import java.util.List;

/** A validated, still-ephemeral task candidate shown before confirmation. */
public record LearningPlanAiTaskSuggestion(
        String title,
        String description,
        Integer plannedMinutes,
        LocalDate dueDate,
        Integer sortOrder,
        List<String> evidenceKeys) {

    /** Evidence objects are resolved from the response-level map by clients. */
    public List<String> evidence() {
        return evidenceKeys;
    }
}
