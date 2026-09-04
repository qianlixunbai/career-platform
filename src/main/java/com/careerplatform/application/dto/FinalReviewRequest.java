package com.careerplatform.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class FinalReviewRequest {

    @NotBlank(message = "复盘总结不能为空")
    @Size(max = 16_000, message = "复盘总结长度不能超过16000个字符")
    private String summary;

    @Size(max = 16_000, message = "经验总结长度不能超过16000个字符")
    private String lessonsLearned;

    @Size(max = 16_000, message = "改进方向长度不能超过16000个字符")
    private String improvements;

    @Min(value = 1, message = "评分不能低于1")
    @Max(value = 5, message = "评分不能高于5")
    private Integer rating;

    @NotNull(message = "复盘时间不能为空")
    private LocalDateTime reviewedAt;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getLessonsLearned() {
        return lessonsLearned;
    }

    public void setLessonsLearned(String lessonsLearned) {
        this.lessonsLearned = lessonsLearned;
    }

    public String getImprovements() {
        return improvements;
    }

    public void setImprovements(String improvements) {
        this.improvements = improvements;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
