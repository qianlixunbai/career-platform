package com.careerplatform.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.resume.enums.ResumeVersionStatus;
import java.time.LocalDateTime;

@TableName("resume_version")
public class ResumeVersion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long resumeId;
    private Integer versionNo;
    private String label;
    private ResumeVersionStatus status;
    private LocalDateTime finalizedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public ResumeVersionStatus getStatus() { return status; }
    public void setStatus(ResumeVersionStatus status) { this.status = status; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(LocalDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
