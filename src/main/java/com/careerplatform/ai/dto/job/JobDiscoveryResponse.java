package com.careerplatform.ai.dto.job;

import java.util.List;

/** Discovery response containing only ephemeral review candidates. */
public record JobDiscoveryResponse(List<JobCandidate> candidates, List<String> warnings, int searchCalls) {
    public JobDiscoveryResponse {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
