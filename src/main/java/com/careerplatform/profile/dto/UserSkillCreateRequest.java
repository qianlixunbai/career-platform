package com.careerplatform.profile.dto;

import com.careerplatform.profile.enums.Proficiency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class UserSkillCreateRequest {
    @NotNull(message = "技能不能为空") @Positive(message = "技能 ID 必须为正数") private Long skillId;
    @NotNull(message = "掌握程度不能为空") private Proficiency proficiency;
    public Long getSkillId() { return skillId; } public void setSkillId(Long skillId) { this.skillId = skillId; }
    public Proficiency getProficiency() { return proficiency; } public void setProficiency(Proficiency proficiency) { this.proficiency = proficiency; }
}
