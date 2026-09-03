package com.careerplatform.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.career.enums.JobType;
import com.careerplatform.career.enums.SourceType;
import java.time.LocalDate;

@TableName("job")
public class Job {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long companyId;
    private String title;
    private String city;
    private JobType jobType;
    private LocalDate publishDate;
    private LocalDate deadline;
    private String rawJd;
    private SourceType sourceType;
    private String sourceName;
    private String sourceUrl;
    private Boolean archived;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public Long getCompanyId() { return companyId; } public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getCity() { return city; } public void setCity(String city) { this.city = city; }
    public JobType getJobType() { return jobType; } public void setJobType(JobType jobType) { this.jobType = jobType; }
    public LocalDate getPublishDate() { return publishDate; } public void setPublishDate(LocalDate publishDate) { this.publishDate = publishDate; }
    public LocalDate getDeadline() { return deadline; } public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getRawJd() { return rawJd; } public void setRawJd(String rawJd) { this.rawJd = rawJd; }
    public SourceType getSourceType() { return sourceType; } public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public String getSourceName() { return sourceName; } public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceUrl() { return sourceUrl; } public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public Boolean getArchived() { return archived; } public void setArchived(Boolean archived) { this.archived = archived; }
}
