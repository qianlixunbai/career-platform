package com.careerplatform.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class LearningMaterialRequest {

    @Positive(message = "任务ID必须为正数")
    private Long taskId;

    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;

    @Size(max = 2_048, message = "资料链接长度不能超过2048个字符")
    private String sourceUrl;

    @Size(max = 16_000, message = "资料描述长度不能超过16000个字符")
    private String description;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
