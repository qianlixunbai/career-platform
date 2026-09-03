package com.careerplatform.career.dto;

import com.careerplatform.career.enums.JobType;
import com.careerplatform.career.enums.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class JobRequest {
    @NotNull(message = "公司不能为空") private Long companyId;
    @NotBlank(message = "岗位名称不能为空") @Size(max = 150, message = "岗位名称长度不能超过150个字符") private String title;
    @Size(max = 100, message = "城市长度不能超过100个字符") private String city;
    @NotNull(message = "岗位类型不能为空") private JobType jobType;
    private LocalDate publishDate;
    private LocalDate deadline;
    @Size(max = 16000, message = "岗位描述长度不能超过16000个字符") private String rawJd;
    @NotNull(message = "来源类型不能为空") private SourceType sourceType;
    @Size(max = 150, message = "来源名称长度不能超过150个字符") private String sourceName;
    @Size(max = 500, message = "来源链接长度不能超过500个字符") private String sourceUrl;
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
}
