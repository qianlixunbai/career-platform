package com.careerplatform.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SkillCreateRequest {
    @NotBlank(message = "技能名称不能为空") @Size(max = 100, message = "技能名称长度不能超过100个字符") private String name;
    public String getName() { return name; } public void setName(String name) { this.name = name; }
}
