package com.careerplatform.career.dto;

import com.careerplatform.career.enums.CareerGoalStatus;

public record CareerGoalResponse(Long id, String targetPosition, String targetCity, String targetIndustry,
                                 String targetCompanyPreference, String salaryExpectation, String notes,
                                 CareerGoalStatus status) { }
