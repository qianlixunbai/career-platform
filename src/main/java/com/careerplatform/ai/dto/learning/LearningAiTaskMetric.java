package com.careerplatform.ai.dto.learning;

import com.careerplatform.learning.enums.LearningTaskStatus;

/** Deterministic per-task review metrics computed from persisted Learning data. */
public record LearningAiTaskMetric(
        Long taskId,
        String title,
        LearningTaskStatus status,
        int plannedMinutes,
        int actualMinutes,
        int studyRecordCount) {
}
