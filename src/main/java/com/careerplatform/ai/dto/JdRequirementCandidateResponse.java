package com.careerplatform.ai.dto;

import com.careerplatform.career.enums.RequirementType;

public record JdRequirementCandidateResponse(
        RequirementType requirementType,
        String description,
        String skillName,
        String evidenceQuote,
        Long matchedSkillId,
        String matchedSkillName,
        SkillResolutionStatus resolutionStatus,
        DuplicateStatus duplicateStatus,
        boolean selected) {
}
