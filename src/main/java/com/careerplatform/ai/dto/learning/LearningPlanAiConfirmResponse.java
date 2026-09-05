package com.careerplatform.ai.dto.learning;

import com.careerplatform.learning.dto.LearningPlanResponse;
import com.careerplatform.learning.dto.LearningTaskResponse;

import java.util.List;

public record LearningPlanAiConfirmResponse(
        LearningPlanResponse plan,
        List<LearningTaskResponse> tasks,
        int totalPlannedMinutes) {
}
