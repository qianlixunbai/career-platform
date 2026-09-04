package com.careerplatform.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public class JdParseConfirmRequest {
    @NotBlank(message = "来源指纹不能为空")
    @Pattern(regexp = "[0-9a-f]{64}", message = "来源指纹格式无效")
    private String sourceFingerprint;

    @NotNull(message = "岗位要求不能为空")
    @Size(max = 50, message = "一次最多确认50条岗位要求")
    @Valid
    private List<JdConfirmRequirementRequest> requirements;

    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public List<JdConfirmRequirementRequest> getRequirements() { return requirements; }
    public void setRequirements(List<JdConfirmRequirementRequest> requirements) { this.requirements = requirements; }
}
