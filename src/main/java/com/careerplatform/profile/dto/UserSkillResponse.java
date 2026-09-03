package com.careerplatform.profile.dto;

import com.careerplatform.profile.enums.Proficiency;

public record UserSkillResponse(Long id, Long skillId, String skillName, Proficiency proficiency) {
}
