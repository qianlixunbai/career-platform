package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Untrusted typed structured output returned by the provider for plan advice. */
public class LearningPlanAiResult {
    private String mainGoal;
    private String rationale;
    private List<LearningPlanAiTaskResult> tasks;
    private List<String> warnings;

    public String getMainGoal() {
        return mainGoal;
    }

    public void setMainGoal(String mainGoal) {
        this.mainGoal = mainGoal;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public List<LearningPlanAiTaskResult> getTasks() {
        return tasks;
    }

    public void setTasks(List<LearningPlanAiTaskResult> tasks) {
        this.tasks = tasks;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
