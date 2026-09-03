package com.careerplatform.career.dto;

import com.careerplatform.career.enums.CareerGoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CareerGoalRequest {
    @NotBlank(message = "目标岗位不能为空") @Size(max = 100, message = "目标岗位长度不能超过100个字符") private String targetPosition;
    @Size(max = 100, message = "目标城市长度不能超过100个字符") private String targetCity;
    @Size(max = 100, message = "目标行业长度不能超过100个字符") private String targetIndustry;
    @Size(max = 255, message = "目标公司偏好长度不能超过255个字符") private String targetCompanyPreference;
    @Size(max = 100, message = "薪资期望长度不能超过100个字符") private String salaryExpectation;
    @Size(max = 16000, message = "备注长度不能超过16000个字符") private String notes;
    @NotNull(message = "目标状态不能为空") private CareerGoalStatus status;
    public String getTargetPosition() { return targetPosition; } public void setTargetPosition(String targetPosition) { this.targetPosition = targetPosition; }
    public String getTargetCity() { return targetCity; } public void setTargetCity(String targetCity) { this.targetCity = targetCity; }
    public String getTargetIndustry() { return targetIndustry; } public void setTargetIndustry(String targetIndustry) { this.targetIndustry = targetIndustry; }
    public String getTargetCompanyPreference() { return targetCompanyPreference; } public void setTargetCompanyPreference(String targetCompanyPreference) { this.targetCompanyPreference = targetCompanyPreference; }
    public String getSalaryExpectation() { return salaryExpectation; } public void setSalaryExpectation(String salaryExpectation) { this.salaryExpectation = salaryExpectation; }
    public String getNotes() { return notes; } public void setNotes(String notes) { this.notes = notes; }
    public CareerGoalStatus getStatus() { return status; } public void setStatus(CareerGoalStatus status) { this.status = status; }
}
