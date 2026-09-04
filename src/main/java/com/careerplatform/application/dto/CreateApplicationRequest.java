package com.careerplatform.application.dto;

import jakarta.validation.constraints.NotNull;

public class CreateApplicationRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @NotNull(message = "简历版本不能为空")
    private Long resumeVersionId;

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
}
