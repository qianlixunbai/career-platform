package com.careerplatform.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResumeRequest {

    @NotBlank(message = "简历名称不能为空")
    @Size(max = 200, message = "简历名称长度不能超过200个字符")
    private String name;

    @Size(max = 2_000, message = "简历描述长度不能超过2000个字符")
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
