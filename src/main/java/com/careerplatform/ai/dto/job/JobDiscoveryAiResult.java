package com.careerplatform.ai.dto.job;

import java.util.List;

/**
 * The only structured result accepted from the discovery model. Provider
 * source facts deliberately do not appear here; the Java service resolves
 * each opaque result key against the request-local search session.
 */
public record JobDiscoveryAiResult(List<Advice> candidates, List<String> warnings) {

    /** Model advice attached to one result key from the current tool session. */
    public record Advice(
            String resultKey,
            Integer rank,
            String fitSummary,
            List<String> strengths,
            List<String> gaps,
            List<String> uncertainty,
            List<String> matchedSkillKeys) {
    }
}
