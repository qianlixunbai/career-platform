package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiInvalidResponseException.Rule;
import com.careerplatform.ai.exception.AiInvalidResponseException.Stage;
import com.careerplatform.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.ai.service.JobSearchSession;
import com.careerplatform.ai.tool.JobSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Deterministic Spring AI tool-calling tests with an HTTP-shaped model exchange. */
class SpringAiToolCallingGatewayTest {

    static java.util.stream.Stream<Arguments> finalFailureShapes() {
        return java.util.stream.Stream.of(
                Arguments.of("{invalid", Rule.FINAL_RESPONSE_JSON_SYNTAX_INVALID,
                        com.fasterxml.jackson.core.JsonParseException.class),
                Arguments.of("{\"candidates\":{}}", Rule.FINAL_RESPONSE_MAPPING_FAILED,
                        com.fasterxml.jackson.databind.exc.MismatchedInputException.class),
                Arguments.of("{\"candidates\":[{\"rank\":\"not-a-number\"}]}", Rule.FINAL_RESPONSE_MAPPING_FAILED,
                        com.fasterxml.jackson.databind.exc.InvalidFormatException.class),
                Arguments.of("{\"candidates\":[],\"Authorization: Bearer synthetic-d1-secret\":1}",
                        Rule.FINAL_RESPONSE_UNKNOWN_PROPERTY,
                        com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class));
    }

    @ParameterizedTest
    @MethodSource("finalFailureShapes")
    @ExtendWith(OutputCaptureExtension.class)
    void finalFailuresUseActualJacksonTaxonomy(String json, Rule rule, Class<? extends Throwable> type,
            CapturedOutput output) {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                .isTrue();
        assertThatThrownBy(() -> mapper.readValue(json,
                com.careerplatform.ai.dto.job.JobDiscoveryAiResult.class)).isInstanceOf(type);
        var model = new ScriptedChatModel(toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
        assertFinalFailure(new SpringAiToolCallingGateway(configuredProperties(), model), rule, json, output);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"plain", "fence", "thinking"})
    void supportedCleanersPreserveSuccessfulParsing(String wrapper) {
        String json = "{\"candidates\":[],\"warnings\":[\"unchanged\"]}";
        String text = switch (wrapper) {
            case "fence" -> "```\n" + json + "\n```";
            case "thinking" -> "<reasoning>ignored</reasoning>\n```json\n" + json + "\n```";
            default -> "  " + json + "  ";
        };
        var model = new ScriptedChatModel(toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        var result = new SpringAiToolCallingGateway(configuredProperties(), model).discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession()));
        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).containsExactly("unchanged");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"cleaner", "deserializeIo", "deserializeRuntime"})
    @ExtendWith(OutputCaptureExtension.class)
    void injectedFailuresDiscardSensitiveCauses(String path, CapturedOutput output) throws Exception {
        String secret = "Authorization: Bearer synthetic-d1-secret";
        String json = "{\"candidates\":[],\"warnings\":[\"" + secret + "\"]}";
        ObjectMapper mapper = org.mockito.Mockito.spy(new ObjectMapper().findAndRegisterModules());
        org.springframework.ai.converter.ResponseTextCleaner cleaner = text -> text;
        Rule rule = Rule.FINAL_RESPONSE_DESERIALIZATION_FAILED;
        if (path.equals("cleaner")) {
            cleaner = text -> { throw new IllegalStateException(json); };
            rule = Rule.FINAL_RESPONSE_CLEANER_FAILED;
        } else {
            Exception failure = path.equals("deserializeIo")
                    ? new com.fasterxml.jackson.core.JsonProcessingException(json) { }
                    : new IllegalStateException(json);
            org.mockito.Mockito.doThrow(failure).when(mapper).readValue(json,
                    com.careerplatform.ai.dto.job.JobDiscoveryAiResult.class);
        }
        var model = new ScriptedChatModel(toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
        var gateway = new SpringAiToolCallingGateway(configuredProperties(), model, mapper,
                org.springframework.ai.model.tool.DefaultToolCallingManager.builder().build(), cleaner);
        assertFinalFailure(gateway, rule, json, output);
        if (path.equals("cleaner")) {
            org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).readValue(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq(com.careerplatform.ai.dto.job.JobDiscoveryAiResult.class));
        }
    }

    private static void assertFinalFailure(SpringAiToolCallingGateway gateway, Rule rule, String raw,
            CapturedOutput output) {
        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessage("AI 返回内容不符合结构化格式").hasNoCause()
                .satisfies(error -> {
                    var failure = (AiInvalidResponseException) error;
                    assertThat(failure.getStage()).isEqualTo(Stage.FINAL_RESPONSE_PARSE);
                    assertThat(failure.getRuleId()).isEqualTo(rule);
                    assertThat(failure.getSuppressed()).isEmpty();
                    new GlobalExceptionHandler().handleAiInvalidResponse(failure);
                });
        assertThat(output.getAll()).contains("stage=FINAL_RESPONSE_PARSE rule=" + rule)
                .doesNotContain(raw, "Authorization: Bearer synthetic-d1-secret");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void invalidFinalJsonDoesNotExposeModelTextInLogsOrException(CapturedOutput output) {
        String privateText = "Authorization: Bearer synthetic-private-model-output-4711";
        String rawJson = "{invalid " + privateText;
        ScriptedChatModel model = new ScriptedChatModel(
                toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                new ChatResponse(List.of(new Generation(new AssistantMessage(rawJson)))));
        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(configuredProperties(), model);

        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessage("AI 返回内容不符合结构化格式")
                .hasNoCause()
                .satisfies(error -> {
                    var failure = (AiInvalidResponseException) error;
                    assertThat(failure.getStage()).isEqualTo(Stage.FINAL_RESPONSE_PARSE);
                    assertThat(failure.getRuleId()).isEqualTo(Rule.FINAL_RESPONSE_JSON_SYNTAX_INVALID);
                    new GlobalExceptionHandler().handleAiInvalidResponse(failure);
                });
        assertThat(output.getAll()).doesNotContain(privateText, rawJson);
        assertThat(output.getAll()).contains("stage=FINAL_RESPONSE_PARSE rule=FINAL_RESPONSE_JSON_SYNTAX_INVALID");
    }

    static java.util.stream.Stream<Arguments> argumentShapes() {
        return java.util.stream.Stream.of(
                Arguments.of("3.0", Rule.MAX_RESULTS_TYPE_INVALID),
                Arguments.of("\"3\"", Rule.MAX_RESULTS_TYPE_INVALID),
                Arguments.of("null", Rule.MAX_RESULTS_TYPE_INVALID),
                Arguments.of("missing", Rule.MAX_RESULTS_MISSING),
                Arguments.of("11", Rule.MAX_RESULTS_OUT_OF_RANGE),
                Arguments.of("0", Rule.MAX_RESULTS_OUT_OF_RANGE),
                Arguments.of("2147483648", Rule.MAX_RESULTS_OUT_OF_RANGE),
                Arguments.of("extra", Rule.TOOL_ARGUMENTS_EXTRA_FIELD),
                Arguments.of("malformed", Rule.TOOL_ARGUMENTS_INVALID_JSON));
    }

    @ParameterizedTest
    @MethodSource("argumentShapes")
    @ExtendWith(OutputCaptureExtension.class)
    void invalidArgumentShapesKeepRulesAndDoNotLeak(String value, Rule rule, CapturedOutput output) {
        String secret = "Authorization: Bearer synthetic-tool-secret-4831";
        String arguments = "{\"query\":\"" + secret + "\",\"location\":\"Shanghai\""
                + (value.equals("missing") ? "" : ",\"maxResults\":"
                    + (value.equals("extra") || value.equals("malformed") ? "3" : value))
                + (value.equals("extra") ? ",\"unexpected\":\"" + secret + "\"" : "")
                + (value.equals("malformed") ? "," : "}");
        ScriptedChatModel model = new ScriptedChatModel(toolChatResponse(
                new AssistantMessage.ToolCall("call-1", "function", "searchJobs", arguments)));
        RecordingSearchGateway search = new RecordingSearchGateway();
        assertThatThrownBy(() -> new SpringAiToolCallingGateway(configuredProperties(), model)
                .discover("system", "user", new JobSearchTool(search, new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class).hasNoCause()
                .satisfies(error -> {
                    var failure = (AiInvalidResponseException) error;
                    assertThat(failure.getStage()).isEqualTo(Stage.TOOL_ARGUMENT_VALIDATION);
                    assertThat(failure.getRuleId()).isEqualTo(rule);
                    assertThat(failure.toString()).doesNotContain(secret, arguments);
                    new GlobalExceptionHandler().handleAiInvalidResponse(failure);
                });
        assertThat(search.calls).isZero();
        assertThat(model.prompts).hasSize(1);
        assertThat(output.getAll()).contains("stage=TOOL_ARGUMENT_VALIDATION rule=" + rule)
                .doesNotContain(secret, arguments);
    }

    @Test
    void integralThreeRemainsAccepted() {
        ScriptedChatModel model = new ScriptedChatModel(
                toolChatResponse(searchCall("call-1", "java", "Shanghai", 3)),
                finalChatResponse(finalResponse("valid")));
        RecordingSearchGateway search = new RecordingSearchGateway();
        var result = new SpringAiToolCallingGateway(configuredProperties(), model).discover(
                "system", "user", new JobSearchTool(search, new JobSearchSession()));
        assertThat(search.calls).isEqualTo(1);
        assertThat(result.warnings()).containsExactly("valid");
    }

    @Test
    void finalJsonKeepsExistingThinkingAndMarkdownCleaning() {
        String wrappedJson = "  <think>ignored reasoning</think>\n```json\n"
                + "{\"candidates\":[],\"warnings\":[\"cleaned\"]}\n```  ";
        ScriptedChatModel model = new ScriptedChatModel(
                toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                new ChatResponse(List.of(new Generation(new AssistantMessage(wrappedJson)))));

        var result = new SpringAiToolCallingGateway(configuredProperties(), model)
                .discover("system", "user", new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession()));

        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).containsExactly("cleaned");
    }

    @Test
    void httpRoundTripUsesFlashOnlyCurrentToolThinkingDisabledAndConvertsFinalSchema() {
        org.springframework.web.client.RestClient.Builder restClientBuilder =
                org.springframework.web.client.RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.tool_choice").value("required"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.tools.length()").value(1))
                .andExpect(jsonPath("$.tools[0].function.name").value("searchJobs"))
                .andExpect(jsonPath("$.parallel_tool_calls").value(false))
                .andExpect(jsonPath("$.max_tokens").value(6000))
                .andRespond(withSuccess(toolCallResponse("call-1", "java", "Shanghai", 2),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.tools.length()").value(1))
                .andRespond(withSuccess(finalResponse("done"), MediaType.APPLICATION_JSON));

        ChatModel model = httpModel(restClientBuilder);
        RecordingSearchGateway searchGateway = new RecordingSearchGateway();
        JobSearchTool tool = new JobSearchTool(searchGateway, new JobSearchSession());
        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(configuredProperties(), model);

        var result = gateway.discover("Find suitable postings.", "Candidate context.", tool);

        assertThat(result.candidates()).isEmpty();
        assertThat(result.warnings()).containsExactly("done");
        assertThat(searchGateway.calls).isEqualTo(1);
        assertThat(searchGateway.lastQuery).isEqualTo("java");
        assertThat(searchGateway.lastLocation).isEqualTo("Shanghai");
        server.verify();
    }

    @Test
    void modelProviderFailureIsNotRetried() {
        org.springframework.web.client.RestClient.Builder restClientBuilder =
                org.springframework.web.client.RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(
                configuredProperties(), httpModel(restClientBuilder));

        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession())))
                .isInstanceOf(com.careerplatform.ai.exception.AiProviderException.class)
                .hasMessage("AI Provider 当前不可用");
        server.verify();
    }

    @Test
    void disabledAiFailsBeforeCallingModel() {
        ScriptedChatModel model = new ScriptedChatModel(finalChatResponse(finalResponse("unused")));
        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(
                new AiProperties(), model);

        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession())))
                .isInstanceOf(AiServiceUnavailableException.class);
        assertThat(model.prompts).isEmpty();
    }

    @Test
    void firstResponseWithoutRequiredToolIsRejected() {
        ScriptedChatModel model = new ScriptedChatModel(finalChatResponse(finalResponse("ignored")));
        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(configuredProperties(), model);

        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(new RecordingSearchGateway(), new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("搜索工具");
        assertThat(model.prompts).hasSize(1);
        assertThat(((OpenAiChatOptions) model.prompts.getFirst().getOptions()).getToolChoice())
                .isEqualTo("required");
    }

    @Test
    void unknownToolIsRejectedBeforeAnyProviderCall() {
        ScriptedChatModel model = new ScriptedChatModel(toolChatResponse(
                new AssistantMessage.ToolCall("call-1", "function", "readAnything", "{}")));
        RecordingSearchGateway searchGateway = new RecordingSearchGateway();
        SpringAiToolCallingGateway gateway = new SpringAiToolCallingGateway(configuredProperties(), model);

        assertThatThrownBy(() -> gateway.discover("system", "user",
                new JobSearchTool(searchGateway, new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("未授权");
        assertThat(searchGateway.calls).isZero();
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void maliciousToolArgumentsAreRejectedBeforeProviderCall() {
        AssistantMessage.ToolCall invalidArguments = new AssistantMessage.ToolCall(
                "call-1", "function", "searchJobs",
                "{\"query\":\"java\",\"location\":\"Shanghai\",\"maxResults\":11,\"userId\":\"admin\"}");
        ScriptedChatModel model = new ScriptedChatModel(toolChatResponse(invalidArguments));
        RecordingSearchGateway searchGateway = new RecordingSearchGateway();

        assertThatThrownBy(() -> new SpringAiToolCallingGateway(configuredProperties(), model)
                .discover("system", "user", new JobSearchTool(searchGateway, new JobSearchSession())))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("参数");
        assertThat(searchGateway.calls).isZero();
        assertThat(model.prompts).hasSize(1);
    }

    @Test
    void modelAndToolBudgetsAreEnforcedAndChoiceBecomesNoneAtBudget() {
        ScriptedChatModel model = new ScriptedChatModel(
                toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                toolChatResponse(searchCall("call-2", "spring", "Shanghai", 1)),
                finalChatResponse(finalResponse("budgeted")));
        RecordingSearchGateway searchGateway = new RecordingSearchGateway();
        JobSearchTool tool = new JobSearchTool(searchGateway, new JobSearchSession());

        var result = new SpringAiToolCallingGateway(configuredProperties(), model)
                .discover("system", "user", tool);

        assertThat(result.warnings()).containsExactly("budgeted");
        assertThat(model.prompts).hasSize(3);
        assertThat(((OpenAiChatOptions) model.prompts.get(0).getOptions()).getToolChoice())
                .isEqualTo("required");
        assertThat(((OpenAiChatOptions) model.prompts.get(1).getOptions()).getToolChoice())
                .isEqualTo("auto");
        assertThat(((OpenAiChatOptions) model.prompts.get(2).getOptions()).getToolChoice())
                .isEqualTo("none");
        assertThat(searchGateway.calls).isEqualTo(2);
        assertThat(tool.getCallCount()).isEqualTo(2);
    }

    @Test
    void aThirdToolCallIsRejectedBeforeManagerInvocation() {
        ScriptedChatModel model = new ScriptedChatModel(
                toolChatResponse(searchCall("call-1", "java", "Shanghai", 1)),
                toolChatResponse(searchCall("call-2", "spring", "Shanghai", 1)),
                toolChatResponse(searchCall("call-3", "kotlin", "Shanghai", 1)));
        RecordingSearchGateway searchGateway = new RecordingSearchGateway();
        JobSearchTool tool = new JobSearchTool(searchGateway, new JobSearchSession());

        assertThatThrownBy(() -> new SpringAiToolCallingGateway(configuredProperties(), model)
                .discover("system", "user", tool))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessageContaining("预算");
        assertThat(model.prompts).hasSize(3);
        assertThat(((OpenAiChatOptions) model.prompts.getLast().getOptions()).getToolChoice())
                .isEqualTo("none");
        assertThat(searchGateway.calls).isEqualTo(2);
        assertThat(tool.getCallCount()).isEqualTo(2);
    }

    private static AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setApiKey("test-ai-key");
        return properties;
    }

    private static ChatModel httpModel(org.springframework.web.client.RestClient.Builder restClientBuilder) {
        return SpringAiToolCallingGateway.buildModel("test-ai-key", restClientBuilder);
    }

    private static String toolCallResponse(String id, String query, String location, int maxResults) {
        return """
                {
                  "id":"chatcmpl-tool",
                  "object":"chat.completion",
                  "created":1,
                  "model":"deepseek-v4-flash",
                  "choices":[{
                    "index":0,
                    "message":{"role":"assistant","content":"","tool_calls":[
                      {"id":"%s","type":"function","function":{"name":"searchJobs","arguments":"{\\"query\\":\\"%s\\",\\"location\\":\\"%s\\",\\"maxResults\\":%d}"}}
                    ]},
                    "finish_reason":"tool_calls"
                  }]
                }
                """.formatted(id, query, location, maxResults);
    }

    private static String finalResponse(String warning) {
        return """
                {
                  "id":"chatcmpl-final",
                  "object":"chat.completion",
                  "created":2,
                  "model":"deepseek-v4-flash",
                  "choices":[{
                    "index":0,
                    "message":{"role":"assistant","content":"{\\"candidates\\":[],\\"warnings\\":[\\"%s\\"]}"},
                    "finish_reason":"stop"
                  }]
                }
                """.formatted(warning);
    }

    private static AssistantMessage.ToolCall searchCall(String id, String query, String location, int maxResults) {
        return new AssistantMessage.ToolCall(id, "function", "searchJobs",
                "{\"query\":\"%s\",\"location\":\"%s\",\"maxResults\":%d}"
                        .formatted(query, location, maxResults));
    }

    private static ChatResponse toolChatResponse(AssistantMessage.ToolCall call) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(call))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static ChatResponse finalChatResponse(String responseJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            var root = mapper.readTree(responseJson);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private final List<Prompt> prompts = new ArrayList<>();

        private ScriptedChatModel(ChatResponse... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            prompts.add(prompt);
            ChatResponse response = responses.poll();
            if (response == null) {
                throw new AssertionError("scripted model exhausted");
            }
            return response;
        }
    }

    private static final class RecordingSearchGateway implements JobSearchGateway {
        private int calls;
        private String lastQuery;
        private String lastLocation;

        @Override
        public List<ProviderSearchResult> search(String query, String location, int maxResults) {
            calls++;
            lastQuery = query;
            lastLocation = location;
            return List.of(new ProviderSearchResult(
                    "https://jobs.example.com/" + calls,
                    "Posting " + calls,
                    "jobs.example.com",
                    "Snippet " + calls,
                    null));
        }
    }
}
