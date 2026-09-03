package com.careerplatform.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("internship_experience")
public class InternshipExperience {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private String companyName; private String position; private LocalDate startDate; private LocalDate endDate; private String description;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getCompanyName() { return companyName; } public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getPosition() { return position; } public void setPosition(String position) { this.position = position; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; } public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
}
