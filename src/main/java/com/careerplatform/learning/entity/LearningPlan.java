package com.careerplatform.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.learning.enums.LearningPlanStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("learning_plan")
public class LearningPlan {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String mainGoal;
    private LearningPlanStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getWeekStart() { return weekStart; } public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public LocalDate getWeekEnd() { return weekEnd; } public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }
    public String getMainGoal() { return mainGoal; } public void setMainGoal(String mainGoal) { this.mainGoal = mainGoal; }
    public LearningPlanStatus getStatus() { return status; } public void setStatus(LearningPlanStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
