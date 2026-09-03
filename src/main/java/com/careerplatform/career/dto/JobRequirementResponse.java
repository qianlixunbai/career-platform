package com.careerplatform.career.dto;

import com.careerplatform.career.enums.RequirementType;

public record JobRequirementResponse(Long id, RequirementType requirementType, Long skillId,
                                     String requirementText) { }
