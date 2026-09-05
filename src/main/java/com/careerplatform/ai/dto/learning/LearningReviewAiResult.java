package com.careerplatform.ai.dto.learning;

import java.util.List;

/** Untrusted typed structured output returned by the provider for review advice. */
public class LearningReviewAiResult {
    private String summary;
    private List<LearningAiReviewItemResult> achievements;
    private List<LearningAiReviewItemResult> problems;
    private List<LearningAiReviewItemResult> nextSteps;
    private List<String> warnings;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<LearningAiReviewItemResult> getAchievements() {
        return achievements;
    }

    public void setAchievements(List<LearningAiReviewItemResult> achievements) {
        this.achievements = achievements;
    }

    public List<LearningAiReviewItemResult> getProblems() {
        return problems;
    }

    public void setProblems(List<LearningAiReviewItemResult> problems) {
        this.problems = problems;
    }

    public List<LearningAiReviewItemResult> getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(List<LearningAiReviewItemResult> nextSteps) {
        this.nextSteps = nextSteps;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
