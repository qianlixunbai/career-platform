package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.dto.job.JobDiscoveryRequest;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiInvalidResponseException.Rule;
import com.careerplatform.ai.exception.AiInvalidResponseException.Stage;
import com.careerplatform.ai.service.JobDiscoveryCandidateStore;
import com.careerplatform.ai.service.JobDiscoveryContextBuilder;
import com.careerplatform.ai.service.JobDiscoveryPromptFactory;
import com.careerplatform.ai.service.JobDiscoveryService;
import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.service.CareerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real gateway, tool/session and service; only model and provider I/O are fixtures. */
class JobDiscoveryOfflineFlowTest {
    @ParameterizedTest
    @ValueSource(strings = {"valid", "unknownKey", "nullAdvice", "missingAdvice"})
    void nonEmptyJsonFlowsThroughRequestLocalReconstruction(String variant) {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger modelCalls = new AtomicInteger();
        JobSearchGateway search = mock(JobSearchGateway.class);
        CareerService career = mock(CareerService.class);
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        JobDiscoveryContextBuilder context = mock(JobDiscoveryContextBuilder.class);
        var provider = new JobSearchGateway.ProviderSearchResult(
                "https://careers.example.com/jobs/42", "Provider title", "careers.example.com",
                "Provider snippet", LocalDate.of(2026, 9, 1));
        when(search.search("backend", "Shanghai", 3)).thenReturn(List.of(provider));
        when(context.build(eq(51L), any())).thenReturn(new JobDiscoveryContextBuilder.DiscoveryContext(
                new CareerGoal(), "backend", "Shanghai", 3, List.of(), Map.of(), List.of(), "context", 7));

        ChatModel model = prompt -> {
            if (modelCalls.incrementAndGet() == 1) {
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "searchJobs",
                                "{\"query\":\"backend\",\"location\":\"Shanghai\",\"maxResults\":3}")))
                        .build())));
            }
            assertThat(modelCalls.get()).isEqualTo(2);
            var toolMessage = (ToolResponseMessage) prompt.getInstructions().getLast();
            try {
                String toolData = toolMessage.getResponses().getFirst().responseData();
                String key = mapper.readTree(toolData).get(0).get("resultKey").asText();
                assertThat(key).startsWith("result-");
                assertThat(toolData).doesNotContain(provider.sourceUrl());
                var json = mapper.createObjectNode();
                json.putArray("warnings");
                var advice = json.putArray("candidates").addObject();
                advice.put("resultKey", variant.equals("unknownKey") ? "unknown-result" : key);
                advice.put("rank", 1);
                advice.put("fitSummary", "Relevant backend position");
                advice.putArray("strengths");
                advice.putArray("gaps");
                advice.putArray("uncertainty");
                if (variant.equals("nullAdvice")) advice.putNull("matchedSkillKeys");
                else if (!variant.equals("missingAdvice")) advice.putArray("matchedSkillKeys");
                String finalJson = mapper.writeValueAsString(json);
                assertThat(finalJson).doesNotContain("sourceUrl", provider.sourceUrl(), "publishedAt");
                return new ChatResponse(List.of(new Generation(new AssistantMessage(finalJson))));
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        };
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setApiKey("synthetic-offline-key");
        var store = new JobDiscoveryCandidateStore(Clock.systemUTC(), Duration.ofMinutes(15), 50, 1000);
        var service = new JobDiscoveryService(new SpringAiToolCallingGateway(properties, model),
                context, new JobDiscoveryPromptFactory(), search, store, career, transaction);
        // Constructor configures propagation; discovery must never begin a transaction.
        clearInvocations(transaction);
        var request = new JobDiscoveryRequest(61L, "backend", "Shanghai", 3);
        if (variant.equals("valid")) {
            var response = service.discover(51L, request);
            assertThat(response.searchCalls()).isEqualTo(1);
            assertThat(response.candidates()).hasSize(1);
            var candidate = response.candidates().getFirst();
            assertThat(candidate.candidateId()).isNotBlank();
            assertThat(candidate.expiresAt()).isNotNull();
            assertThat(candidate.sourceFacts().sourceUrl()).isEqualTo(provider.sourceUrl());
            assertThat(candidate.sourceFacts().sourceTitle()).isEqualTo(provider.sourceTitle());
            assertThat(candidate.sourceFacts().sourceHost()).isEqualTo(provider.sourceHost());
            assertThat(candidate.sourceFacts().sourceSnippet()).isEqualTo(provider.sourceSnippet());
            assertThat(candidate.sourceFacts().publishedAt()).isEqualTo(provider.publishedAt());
            assertThat(candidate.aiAdvice().rank()).isEqualTo(1);
            assertThat(candidate.aiAdvice().fitSummary()).isEqualTo("Relevant backend position");
            assertThat(candidate.aiAdvice().strengths()).isEmpty();
            assertThat(candidate.aiAdvice().gaps()).isEmpty();
            assertThat(candidate.aiAdvice().uncertainty()).isEmpty();
            assertThat(candidate.aiAdvice().matchedSkills()).isEmpty();
            assertThat(store.size()).isEqualTo(1);
        } else {
            assertThatThrownBy(() -> service.discover(51L, request))
                    .isInstanceOf(AiInvalidResponseException.class).hasNoCause()
                    .satisfies(error -> {
                        var failure = (AiInvalidResponseException) error;
                        assertThat(failure.getRuleId()).isEqualTo(variant.equals("unknownKey")
                                ? Rule.UNKNOWN_RESULT_KEY : Rule.ADVICE_LIST_NULL);
                        assertThat(failure.getStage()).isEqualTo(variant.equals("unknownKey")
                                ? Stage.RESULT_KEY_RESOLUTION : Stage.SERVICE_RESULT_VALIDATION);
                    });
            assertThat(store.size()).isZero();
        }
        assertThat(modelCalls.get()).isEqualTo(2);
        verify(search).search("backend", "Shanghai", 3);
        verifyNoInteractions(career, transaction);
    }
}
