package com.careerplatform.ai.dto.job;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ephemeral candidate returned to the client for review. Source facts,
 * extracted display fields, and probabilistic AI advice stay visibly
 * separated in the response shape.
 */
public record JobCandidate(
        String candidateId,
        Instant expiresAt,
        SourceFacts sourceFacts,
        ExtractedFields extractedFields,
        AiAdvice aiAdvice) {

    public JobCandidate {
        if (sourceFacts != null) {
            sourceFacts = new SourceFacts(sourceFacts.sourceUrl(), sourceFacts.sourceTitle(),
                    sourceFacts.sourceHost(), sourceFacts.sourceSnippet(), sourceFacts.publishedAt(),
                    sourceFacts.discoveredBy());
        }
        if (extractedFields != null) {
            extractedFields = new ExtractedFields(extractedFields.jobTitle(), extractedFields.companyName(),
                    extractedFields.location(), extractedFields.jobTypeSuggestion());
        }
        if (aiAdvice != null) {
            aiAdvice = new AiAdvice(aiAdvice.rank(), aiAdvice.fitSummary(), aiAdvice.strengths(),
                    aiAdvice.gaps(), aiAdvice.uncertainty(), aiAdvice.matchedSkills());
        }
    }

    /** Facts copied from the trusted search provider response. */
    public record SourceFacts(
            String sourceUrl,
            String sourceTitle,
            String sourceHost,
            String sourceSnippet,
            LocalDate publishedAt,
            String discoveredBy) {
    }

    /** Display fields derived by Java from the trusted context/result. */
    public record ExtractedFields(
            String jobTitle,
            String companyName,
            String location,
            String jobTypeSuggestion) {
    }

    /** Advice returned by the model after it referenced a result key. */
    public record AiAdvice(
            int rank,
            String fitSummary,
            List<String> strengths,
            List<String> gaps,
            List<String> uncertainty,
            List<MatchedSkill> matchedSkills) {

        public AiAdvice {
            strengths = immutableList(strengths);
            gaps = immutableList(gaps);
            uncertainty = immutableList(uncertainty);
            matchedSkills = immutableList(matchedSkills);
        }
    }

    /** A canonical user skill matched by the model. */
    public record MatchedSkill(String key, String name) {
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
