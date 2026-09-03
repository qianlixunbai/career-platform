package com.careerplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class EducationExperienceRequest {
    @NotBlank(message = "学校名称不能为空") @Size(max = 150, message = "学校名称长度不能超过150个字符") private String schoolName;
    @NotBlank(message = "专业不能为空") @Size(max = 150, message = "专业长度不能超过150个字符") private String major;
    @NotBlank(message = "学历不能为空") @Size(max = 50, message = "学历长度不能超过50个字符") private String degree;
    @NotNull(message = "开始日期不能为空") private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 5000, message = "描述长度不能超过5000个字符") private String description;
    public String getSchoolName() { return schoolName; } public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getMajor() { return major; } public void setMajor(String major) { this.major = major; }
    public String getDegree() { return degree; } public void setDegree(String degree) { this.degree = degree; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
