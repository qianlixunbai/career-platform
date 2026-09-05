package com.careerplatform.ai.service;

import com.careerplatform.ai.dto.learning.LearningAiEvidence;
import com.careerplatform.ai.dto.learning.LearningAiEvidenceType;
import com.careerplatform.ai.dto.learning.LearningPlanAiSuggestionRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LearningAiPromptFactoryTest {
    @Test
    void systemPromptDefinesUntrustedBoundaryAndNoTools() {
        String system = new LearningAiPromptFactory().systemInstruction();

        assertThat(system).containsIgnoringCase("untrusted")
                .containsIgnoringCase("no tools")
                .containsIgnoringCase("network")
                .containsIgnoringCase("schema")
                .contains("secrets");
    }

    @Test
    void planPromptIncludesOnlyStructuredEvidenceAndNeverRawJd() {
        LearningPlanAiSuggestionRequest request = new LearningPlanAiSuggestionRequest();
        request.setWeekStart(LocalDate.of(2026, 9, 7));
        request.setWeekEnd(LocalDate.of(2026, 9, 13));
        request.setAvailableMinutes(120);
        LearningAiEvidence requirement = new LearningAiEvidence(
                "JOB_REQUIREMENT:3", LearningAiEvidenceType.JOB_REQUIREMENT,
                "结构化岗位要求", "熟悉 Redis");
        var context = new LearningAiContextBuilder.PlanContext(
                request, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Map.of(requirement.key(), requirement), List.of(), 30);

        String prompt = new LearningAiPromptFactory().planUserContent(context);

        assertThat(prompt).contains("JOB_REQUIREMENT:3").contains("熟悉 Redis")
                .doesNotContain("rawJd").doesNotContain("password");
    }
}
