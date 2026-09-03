package com.careerplatform.career.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.careerplatform.career.enums.CareerGoalStatus;

@TableName("career_goal")
public class CareerGoal {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String targetPosition;
    private String targetCity;
    private String targetIndustry;
    private String targetCompanyPreference;
    private String salaryExpectation;
    private String notes;
    private CareerGoalStatus status;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; } public void setUserId(Long userId) { this.userId = userId; }
    public String getTargetPosition() { return targetPosition; } public void setTargetPosition(String targetPosition) { this.targetPosition = targetPosition; }
    public String getTargetCity() { return targetCity; } public void setTargetCity(String targetCity) { this.targetCity = targetCity; }
    public String getTargetIndustry() { return targetIndustry; } public void setTargetIndustry(String targetIndustry) { this.targetIndustry = targetIndustry; }
    public String getTargetCompanyPreference() { return targetCompanyPreference; } public void setTargetCompanyPreference(String targetCompanyPreference) { this.targetCompanyPreference = targetCompanyPreference; }
    public String getSalaryExpectation() { return salaryExpectation; } public void setSalaryExpectation(String salaryExpectation) { this.salaryExpectation = salaryExpectation; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    public CareerGoalStatus getStatus() { return status; } public void setStatus(CareerGoalStatus status) { this.status = status; }
}
