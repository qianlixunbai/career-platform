package com.careerplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class InternshipExperienceRequest {
    @NotBlank(message = "公司名称不能为空") @Size(max = 200, message = "公司名称长度不能超过200个字符") private String companyName;
    @NotBlank(message = "实习岗位不能为空") @Size(max = 100, message = "实习岗位长度不能超过100个字符") private String position;
    @NotNull(message = "开始日期不能为空") private LocalDate startDate;
    private LocalDate endDate;
    @Size(max = 5000, message = "描述长度不能超过5000个字符") private String description;
    public String getCompanyName() { return companyName; } public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getPosition() { return position; } public void setPosition(String position) { this.position = position; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
