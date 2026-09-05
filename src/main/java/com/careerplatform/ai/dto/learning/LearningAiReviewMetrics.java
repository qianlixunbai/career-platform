package com.careerplatform.ai.dto.learning;

import com.careerplatform.learning.enums.LearningTaskStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Java-owned weekly review metrics; no numeric value is trusted from the model. */
public record LearningAiReviewMetrics(
        int taskCount,
        int doneCount,
        int todoCount,
        int inProgressCount,
        int skippedCount,
        BigDecimal completionRate,
        int plannedMinutes,
        int actualMinutes,
        int studyRecordCount,
        Map<LearningTaskStatus, Integer> statusCounts,
        List<LearningAiTaskMetric> tasks) {
}
