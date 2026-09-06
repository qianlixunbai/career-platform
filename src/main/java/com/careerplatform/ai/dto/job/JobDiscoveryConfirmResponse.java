package com.careerplatform.ai.dto.job;

import java.util.List;

/** Result of a successful explicit candidate confirmation. */
public record JobDiscoveryConfirmResponse(Long jobId, List<String> warnings) {
    public JobDiscoveryConfirmResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
