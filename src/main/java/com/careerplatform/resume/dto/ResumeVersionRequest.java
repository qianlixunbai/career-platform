package com.careerplatform.resume.dto;

import jakarta.validation.constraints.Size;

public class ResumeVersionRequest {

    @Size(max = 200, message = "简历版本标签长度不能超过200个字符")
    private String label;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
