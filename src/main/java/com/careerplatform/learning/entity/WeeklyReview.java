package com.careerplatform.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("weekly_review")
public class WeeklyReview {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long planId;
    private String summary;
    private String achievements;
    private String problems;
    private String nextSteps;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanId() { return planId; } public void setPlanId(Long planId) { this.planId = planId; }
    public String getSummary() { return summary; } public void setSummary(String summary) { this.summary = summary; }
    public String getAchievements() { return achievements; } public void setAchievements(String achievements) { this.achievements = achievements; }
    public String getProblems() { return problems; } public void setProblems(String problems) { this.problems = problems; }
    public String getNextSteps() { return nextSteps; } public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }
    public LocalDateTime getReviewedAt() { return reviewedAt; } public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
