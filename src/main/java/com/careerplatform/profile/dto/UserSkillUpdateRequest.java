package com.careerplatform.profile.dto;

import com.careerplatform.profile.enums.Proficiency;
import jakarta.validation.constraints.NotNull;

public class UserSkillUpdateRequest {
    @NotNull(message = "掌握程度不能为空") private Proficiency proficiency;
    public Proficiency getProficiency() { return proficiency; } public void setProficiency(Proficiency proficiency) { this.proficiency = proficiency; }
}
