package com.careerplatform.ai.dto;

import com.careerplatform.career.enums.RequirementType;

/** Model-produced data only. Trusted database identifiers must never appear here. */
public class JdRequirementAiCandidate {
    private RequirementType type;
    private String description;
    private String skillName;
    private String evidenceQuote;

    public RequirementType getType() { return type; }
    public void setType(RequirementType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getEvidenceQuote() { return evidenceQuote; }
    public void setEvidenceQuote(String evidenceQuote) { this.evidenceQuote = evidenceQuote; }
}
