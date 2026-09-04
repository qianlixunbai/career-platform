package com.careerplatform.application.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;

import java.time.LocalDateTime;

@TableName("application_stage_history")
public class ApplicationStageHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long applicationId;
    private ApplicationStage fromStage;
    private ApplicationStage toStage;
    private ApplicationEndReason endReason;
    private String note;
    private LocalDateTime changedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public ApplicationStage getFromStage() {
        return fromStage;
    }

    public void setFromStage(ApplicationStage fromStage) {
        this.fromStage = fromStage;
    }

    public ApplicationStage getToStage() {
        return toStage;
    }

    public void setToStage(ApplicationStage toStage) {
        this.toStage = toStage;
    }

    public ApplicationEndReason getEndReason() {
        return endReason;
    }

    public void setEndReason(ApplicationEndReason endReason) {
        this.endReason = endReason;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
