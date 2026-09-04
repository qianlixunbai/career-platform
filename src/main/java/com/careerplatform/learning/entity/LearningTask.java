package com.careerplatform.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.learning.enums.LearningTaskStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("learning_task")
public class LearningTask {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long planId;
    private String title;
    private String description;
    private LearningTaskStatus status;
    private Integer plannedMinutes;
    private LocalDate dueDate;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanId() { return planId; } public void setPlanId(Long planId) { this.planId = planId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public LearningTaskStatus getStatus() { return status; } public void setStatus(LearningTaskStatus status) { this.status = status; }
    public Integer getPlannedMinutes() { return plannedMinutes; } public void setPlannedMinutes(Integer plannedMinutes) { this.plannedMinutes = plannedMinutes; }
    public LocalDate getDueDate() { return dueDate; } public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
