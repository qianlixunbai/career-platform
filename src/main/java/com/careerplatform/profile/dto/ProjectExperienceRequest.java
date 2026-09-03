package com.careerplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class ProjectExperienceRequest {
    @NotBlank(message = "项目名称不能为空") @Size(max = 200, message = "项目名称长度不能超过200个字符") private String projectName;
    @NotBlank(message = "项目角色不能为空") @Size(max = 100, message = "项目角色长度不能超过100个字符") private String role;
    @NotNull(message = "开始日期不能为空") private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 5000, message = "描述长度不能超过5000个字符") private String description;
    @NotBlank(message = "技术栈不能为空") @Size(max = 500, message = "技术栈长度不能超过500个字符") private String techStack;
    @Size(max = 500, message = "项目地址长度不能超过500个字符") private String projectUrl;
    public String getProjectName() { return projectName; } public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getRole() { return role; } public void setRole(String role) { this.role = role; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getTechStack() { return techStack; } public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getProjectUrl() { return projectUrl; } public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }
}
