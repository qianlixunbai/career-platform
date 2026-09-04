package com.careerplatform.learning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class WeeklyReviewRequest {

    @Size(max = 16_000, message = "复盘总结长度不能超过16000个字符")
    private String summary;

    @Size(max = 16_000, message = "复盘成果长度不能超过16000个字符")
    private String achievements;

    @Size(max = 16_000, message = "复盘问题长度不能超过16000个字符")
    private String problems;

    @Size(max = 16_000, message = "复盘下一步长度不能超过16000个字符")
    private String nextSteps;

    @NotNull(message = "复盘时间不能为空")
    private LocalDateTime reviewedAt;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAchievements() {
        return achievements;
    }

    public void setAchievements(String achievements) {
        this.achievements = achievements;
    }

    public String getProblems() {
        return problems;
    }

    public void setProblems(String problems) {
        this.problems = problems;
    }

    public String getNextSteps() {
        return nextSteps;
    }

    public void setNextSteps(String nextSteps) {
        this.nextSteps = nextSteps;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
