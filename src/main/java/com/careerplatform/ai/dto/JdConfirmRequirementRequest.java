package com.careerplatform.ai.dto;

import com.careerplatform.career.enums.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class JdConfirmRequirementRequest {
    @NotNull(message = "是否选择不能为空")
    private Boolean selected;

    @NotNull(message = "要求类型不能为空")
    private RequirementType requirementType;

    private Long skillId;

    @NotBlank(message = "要求内容不能为空")
    @Size(max = 1000, message = "要求内容长度不能超过1000个字符")
    private String requirementText;

    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
    public RequirementType getRequirementType() { return requirementType; }
    public void setRequirementType(RequirementType requirementType) { this.requirementType = requirementType; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long skillId) { this.skillId = skillId; }
    public String getRequirementText() { return requirementText; }
    public void setRequirementText(String requirementText) { this.requirementText = requirementText; }
}
