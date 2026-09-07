package com.careerplatform.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("learning_material")
public class LearningMaterial {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long planId;
    private Long taskId;
    private String title;
    private String sourceUrl;
    private String description;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String indexStatus;
    private Integer chunkCount;
    private String embeddingIdentity;
    @TableField(select = false)
    private byte[] fileContent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getPlanId() { return planId; } public void setPlanId(Long planId) { this.planId = planId; }
    public Long getTaskId() { return taskId; } public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getSourceUrl() { return sourceUrl; } public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getFileName() { return fileName; } public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; } public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; } public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getIndexStatus() { return indexStatus; } public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public Integer getChunkCount() { return chunkCount; } public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public String getEmbeddingIdentity() { return embeddingIdentity; } public void setEmbeddingIdentity(String embeddingIdentity) { this.embeddingIdentity = embeddingIdentity; }
    public byte[] getFileContent() { return fileContent; } public void setFileContent(byte[] fileContent) { this.fileContent = fileContent; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
