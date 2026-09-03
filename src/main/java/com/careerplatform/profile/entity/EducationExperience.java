package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("education_experience")
public class EducationExperience {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String schoolName; private String major; private String degree;
    private LocalDate startDate; private LocalDate endDate; private String description;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getSchoolName() { return schoolName; } public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    public String getMajor() { return major; } public void setMajor(String major) { this.major = major; }
    public String getDegree() { return degree; } public void setDegree(String degree) { this.degree = degree; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
