package com.careerplatform.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.resume.enums.ResumeSectionType;
import com.careerplatform.resume.enums.ResumeSourceType;
import java.time.LocalDateTime;

@TableName("resume_content_item")
public class ResumeContentItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long versionId;
    private ResumeSectionType sectionType;
    private String title;
    private String content;
    private ResumeSourceType sourceType;
    private Long sourceId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public ResumeSectionType getSectionType() { return sectionType; }
    public void setSectionType(ResumeSectionType sectionType) { this.sectionType = sectionType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public ResumeSourceType getSourceType() { return sourceType; }
    public void setSourceType(ResumeSourceType sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
