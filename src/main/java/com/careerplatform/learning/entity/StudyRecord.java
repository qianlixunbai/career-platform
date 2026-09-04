package com.careerplatform.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("study_record")
public class StudyRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long taskId;
    private LocalDateTime studiedAt;
    private Integer durationMinutes;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getTaskId() { return taskId; } public void setTaskId(Long taskId) { this.taskId = taskId; }
    public LocalDateTime getStudiedAt() { return studiedAt; } public void setStudiedAt(LocalDateTime studiedAt) { this.studiedAt = studiedAt; }
    public Integer getDurationMinutes() { return durationMinutes; } public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getContent() { return content; } public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
