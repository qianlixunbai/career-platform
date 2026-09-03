package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("project_experience")
public class ProjectExperience {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String projectName; private String role; private LocalDate startDate; private LocalDate endDate;
    private String description; private String techStack; private String projectUrl;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getProjectName() { return projectName; } public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getRole() { return role; } public void setRole(String role) { this.role = role; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getTechStack() { return techStack; } public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getProjectUrl() { return projectUrl; } public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }
}
