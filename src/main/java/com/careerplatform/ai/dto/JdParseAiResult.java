package com.careerplatform.ai.dto;

import java.util.List;

/** Typed structured output returned by the AI gateway before deterministic validation. */
public class JdParseAiResult {
    private List<JdRequirementAiCandidate> requirements;
    private List<String> warnings;

    public List<JdRequirementAiCandidate> getRequirements() { return requirements; }
    public void setRequirements(List<JdRequirementAiCandidate> requirements) { this.requirements = requirements; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
