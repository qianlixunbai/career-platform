package com.careerplatform.application.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.application.enums.ApplicationEndReason;
import com.careerplatform.application.enums.ApplicationStage;

import java.time.LocalDateTime;

@TableName("application")
public class Application {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long jobId;
    private Long resumeVersionId;
    private ApplicationStage currentStage;
    private ApplicationEndReason endReason;
    private String endNote;
    private String jobTitleSnapshot;
    private String companyNameSnapshot;
    private String locationSnapshot;
    private String jobDescriptionSnapshot;
    private LocalDateTime appliedAt;
    private LocalDateTime endedAt;
    @TableField(value = "ongoing_job_id", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long ongoingJobId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getResumeVersionId() {
        return resumeVersionId;
    }

    public void setResumeVersionId(Long resumeVersionId) {
        this.resumeVersionId = resumeVersionId;
    }

    public ApplicationStage getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(ApplicationStage currentStage) {
        this.currentStage = currentStage;
    }

    public ApplicationEndReason getEndReason() {
        return endReason;
    }

    public void setEndReason(ApplicationEndReason endReason) {
        this.endReason = endReason;
    }

    public String getEndNote() {
        return endNote;
    }

    public void setEndNote(String endNote) {
        this.endNote = endNote;
    }

    public String getJobTitleSnapshot() {
        return jobTitleSnapshot;
    }

    public void setJobTitleSnapshot(String jobTitleSnapshot) {
        this.jobTitleSnapshot = jobTitleSnapshot;
    }

    public String getCompanyNameSnapshot() {
        return companyNameSnapshot;
    }

    public void setCompanyNameSnapshot(String companyNameSnapshot) {
        this.companyNameSnapshot = companyNameSnapshot;
    }

    public String getLocationSnapshot() {
        return locationSnapshot;
    }

    public void setLocationSnapshot(String locationSnapshot) {
        this.locationSnapshot = locationSnapshot;
    }

    public String getJobDescriptionSnapshot() {
        return jobDescriptionSnapshot;
    }

    public void setJobDescriptionSnapshot(String jobDescriptionSnapshot) {
        this.jobDescriptionSnapshot = jobDescriptionSnapshot;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Long getOngoingJobId() {
        return ongoingJobId;
    }

    public void setOngoingJobId(Long ongoingJobId) {
        this.ongoingJobId = ongoingJobId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
