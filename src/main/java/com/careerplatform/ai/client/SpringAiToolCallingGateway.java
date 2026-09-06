package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.dto.job.JobDiscoveryAiResult;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiInvalidResponseException.Rule;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.ai.tool.JobSearchTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.CompositeResponseTextCleaner;
import org.springframework.ai.converter.MarkdownCodeBlockCleaner;
import org.springframework.ai.converter.ResponseTextCleaner;
import org.springframework.ai.converter.ThinkingTagCleaner;
import org.springframework.ai.converter.WhitespaceCleaner;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Spring AI implementation of the M6C tool-calling boundary.
 *
 * <p>This gateway owns an independent, lazily created OpenAI-compatible model
 * configured for DeepSeek's fixed Flash endpoint. It deliberately does not
 * expose that model as a Spring bean. Tool execution is kept outside
 * {@link OpenAiChatModel}: this class validates the model response, invokes
 * {@link DefaultToolCallingManager} with the current callback, and controls
 * the model/tool budgets.</p>
 */
public class SpringAiToolCallingGateway implements AiToolCallingGateway {

    public static final String DEEPSEEK_BASE_URL = "https://api.deepseek.com";
    public static final String PRODUCTION_MODEL = "deepseek-v4-flash";
    public static final int MAX_TOOL_CALLS = 2;
    public static final int MAX_MODEL_REQUESTS = 3;

    static final Duration MODEL_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration MODEL_REQUEST_TIMEOUT = Duration.ofSeconds(40);

    private static final ResponseTextCleaner RESPONSE_TEXT_CLEANER = CompositeResponseTextCleaner.builder()
            .addCleaner(new WhitespaceCleaner())
            .addCleaner(new ThinkingTagCleaner())
            .addCleaner(new MarkdownCodeBlockCleaner())
            .addCleaner(new WhitespaceCleaner())
            .build();

    private final AiProperties properties;
    private final Supplier<ChatModel> chatModelSupplier;
    private final ObjectMapper objectMapper;
    private final ToolCallingManager toolCallingManager;
    private final ResponseTextCleaner responseTextCleaner;

    private volatile ChatModel productionModel;

    /** Create a lazily initialized production gateway. */
    public SpringAiToolCallingGateway(AiProperties properties) {
        this(properties, null, new ObjectMapper().findAndRegisterModules(), emptyToolCallingManager());
    }

    /** Package-private deterministic seam for a fake ChatModel. */
    SpringAiToolCallingGateway(AiProperties properties, ChatModel chatModel) {
        this(properties, Objects.requireNonNull(chatModel, "chatModel must not be null"),
                new ObjectMapper().findAndRegisterModules(), emptyToolCallingManager());
    }

    /** Package-private deterministic seam with an explicit converter mapper and manager. */
    SpringAiToolCallingGateway(
            AiProperties properties,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            ToolCallingManager toolCallingManager) {
        this(properties, chatModel, objectMapper, toolCallingManager, RESPONSE_TEXT_CLEANER);
    }

    /** Package-private seam for deterministic cleaner failure injection only. */
    SpringAiToolCallingGateway(
            AiProperties properties,
            ChatModel chatModel,
            ObjectMapper objectMapper,
            ToolCallingManager toolCallingManager,
            ResponseTextCleaner responseTextCleaner) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.toolCallingManager = Objects.requireNonNull(
                toolCallingManager, "toolCallingManager must not be null");
        this.responseTextCleaner = Objects.requireNonNull(responseTextCleaner, "responseTextCleaner must not be null");
        this.chatModelSupplier = chatModel == null ? this::productionModel : () -> chatModel;
        this.productionModel = chatModel;
    }

    @Override
    public JobDiscoveryAiResult discover(String systemInstruction, String userContent, JobSearchTool tool) {
        if (!properties.isConfigured()) {
            throw new AiServiceUnavailableException("AI 服务当前未启用");
        }
        Objects.requireNonNull(systemInstruction, "systemInstruction must not be null");
        Objects.requireNonNull(userContent, "userContent must not be null");
        Objects.requireNonNull(tool, "tool must not be null");

        ToolCallback[] toolCallbacks = ToolCallbacks.from(tool);
        validateOnlySearchTool(toolCallbacks);
        BeanOutputConverter<JobDiscoveryAiResult> converter =
                new BeanOutputConverter<>(JobDiscoveryAiResult.class, objectMapper);

        ChatModel chatModel = chatModelSupplier.get();
        if (chatModel == null) {
            throw new AiServiceUnavailableException("AI 服务当前未启用");
        }

        int modelRequests = 1;
        int toolCalls = 0;
        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(systemInstruction + "\n\n" + converter.getFormat()),
                        new UserMessage(userContent)),
                options(toolCallbacks, "required"));
        ChatResponse response = callModel(chatModel, prompt);

        while (true) {
            int responseToolCalls = countToolCalls(response);
            if (responseToolCalls > 0) {
                validateToolCalls(response);
                if (toolCalls + responseToolCalls > MAX_TOOL_CALLS) {
                    throw invalid(Rule.TOOL_BUDGET_EXCEEDED, "AI 请求的搜索次数超过预算");
                }
                if (modelRequests >= MAX_MODEL_REQUESTS) {
                    throw invalid(Rule.MODEL_BUDGET_EXCEEDED, "AI 请求次数超过预算");
                }

                ToolExecutionResult executionResult = executeToolCalls(prompt, response);
                toolCalls += responseToolCalls;
                List<Message> history = executionResult.conversationHistory();
                if (history == null || history.isEmpty()) {
                    throw invalid(Rule.TOOL_HISTORY_EMPTY, "AI 工具执行历史为空");
                }

                String nextToolChoice = toolCalls >= MAX_TOOL_CALLS ? "none" : "auto";
                prompt = new Prompt(history, options(toolCallbacks, nextToolChoice));
                response = callModel(chatModel, prompt);
                modelRequests++;
                continue;
            }

            if (modelRequests == 1) {
                // The first request is explicitly required to call the tool.
                // A provider that ignores that choice must fail closed; Java
                // must never invent a search result on its behalf.
                throw invalid(Rule.REQUIRED_TOOL_MISSING, "AI 未按要求调用搜索工具");
            }
            return convertFinalResponse(response);
        }
    }

    private static void validateOnlySearchTool(ToolCallback[] toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.length != 1
                || toolCallbacks[0] == null
                || !"searchJobs".equals(toolCallbacks[0].getToolDefinition().name())) {
            throw invalid(Rule.TOOL_CONFIGURATION_INVALID, "AI 搜索工具配置无效");
        }
    }

    private static int countToolCalls(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return 0;
        }
        int count = 0;
        for (Generation generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null) {
                continue;
            }
            List<AssistantMessage.ToolCall> calls = generation.getOutput().getToolCalls();
            if (calls != null) {
                count += calls.size();
            }
        }
        return count;
    }

    private void validateToolCalls(ChatResponse response) {
        if (response == null || response.getResults() == null || response.getResults().size() != 1) {
            throw invalid(Rule.TOOL_RESPONSE_INVALID, "AI 工具调用响应无效");
        }
        for (Generation generation : response.getResults()) {
            if (generation == null || generation.getOutput() == null
                    || generation.getOutput().getToolCalls() == null) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : generation.getOutput().getToolCalls()) {
                if (toolCall == null || !"searchJobs".equals(toolCall.name())
                        || !"function".equals(toolCall.type()) || toolCall.id() == null || toolCall.id().isBlank()) {
                    throw invalid(Rule.TOOL_NAME_INVALID, "AI 请求了未授权的工具");
                }
                validateArguments(toolCall.arguments());
            }
        }
    }

    private void validateArguments(String arguments) {
        if (arguments == null || arguments.length() > 3000) throw invalid(Rule.TOOL_ARGUMENTS_INVALID, "AI 工具调用参数无效");
        try {
            var args = objectMapper.readTree(arguments);
            if (args == null || !args.isObject() || !args.path("query").isTextual()) {
                throw invalid(Rule.TOOL_ARGUMENTS_INVALID, "AI 工具调用参数无效");
            }
            if (!args.has("maxResults")) {
                throw invalid(Rule.MAX_RESULTS_MISSING, "AI 工具调用参数无效");
            }
            if (!args.path("maxResults").isIntegralNumber()) {
                throw invalid(Rule.MAX_RESULTS_TYPE_INVALID, "AI 工具调用参数无效");
            }
            if (!args.path("maxResults").canConvertToInt()
                    || args.path("maxResults").intValue() < 1 || args.path("maxResults").intValue() > 10) {
                throw invalid(Rule.MAX_RESULTS_OUT_OF_RANGE, "AI 工具调用参数无效");
            }
            if (args.hasNonNull("location") && !args.get("location").isTextual()) {
                throw invalid(Rule.TOOL_ARGUMENTS_INVALID, "AI 工具调用参数无效");
            }
            var names = args.fieldNames();
            while (names.hasNext()) {
                if (!Set.of("query", "location", "maxResults").contains(names.next())) {
                    throw invalid(Rule.TOOL_ARGUMENTS_EXTRA_FIELD, "AI 工具调用包含未授权参数");
                }
            }
            if (args.get("query").textValue().isBlank() || args.get("query").textValue().length() > 400
                    || args.path("location").asText("").length() > 100) throw invalid(Rule.TOOL_ARGUMENTS_INVALID, "AI 工具调用参数无效");
        } catch (java.io.IOException exception) {
            throw invalid(Rule.TOOL_ARGUMENTS_INVALID_JSON, "AI 工具调用参数无效");
        }
    }

    private ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        try {
            return toolCallingManager.executeToolCalls(prompt, response);
        }
        catch (AiInvalidResponseException | AiProviderException | AiServiceUnavailableException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw invalid(Rule.TOOL_EXECUTION_FAILED, "AI 工具调用参数无效");
        }
    }

    private JobDiscoveryAiResult convertFinalResponse(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw invalid(Rule.FINAL_RESPONSE_EMPTY, "AI 返回为空");
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw invalid(Rule.FINAL_RESPONSE_EMPTY, "AI 返回为空");
        }
        String cleaned;
        try {
            cleaned = responseTextCleaner.clean(text);
        }
        catch (RuntimeException exception) {
            throw invalid(Rule.FINAL_RESPONSE_CLEANER_FAILED, "AI 返回内容不符合结构化格式");
        }
        try {
            // BeanOutputConverter 1.1.8 logs raw model text on parse failure.
            // Keep its default cleaners and schema, but parse without that logging path.
            JobDiscoveryAiResult result = objectMapper.readValue(
                    cleaned, JobDiscoveryAiResult.class);
            if (result == null) {
                throw invalid(Rule.FINAL_RESPONSE_EMPTY, "AI 返回为空");
            }
            return result;
        }
        catch (AiInvalidResponseException exception) {
            throw exception;
        }
        catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException exception) {
            throw invalid(Rule.FINAL_RESPONSE_UNKNOWN_PROPERTY, "AI 返回内容不符合结构化格式");
        }
        catch (com.fasterxml.jackson.core.exc.StreamReadException exception) {
            throw invalid(Rule.FINAL_RESPONSE_JSON_SYNTAX_INVALID, "AI 返回内容不符合结构化格式");
        }
        catch (com.fasterxml.jackson.databind.JsonMappingException exception) {
            throw invalid(Rule.FINAL_RESPONSE_MAPPING_FAILED, "AI 返回内容不符合结构化格式");
        }
        catch (java.io.IOException | RuntimeException exception) {
            throw invalid(Rule.FINAL_RESPONSE_DESERIALIZATION_FAILED, "AI 返回内容不符合结构化格式");
        }
    }

    private static ChatResponse callModel(ChatModel chatModel, Prompt prompt) {
        try {
            ChatResponse response = chatModel.call(prompt);
            if (response == null) {
                throw invalid(Rule.FINAL_RESPONSE_EMPTY, "AI 返回为空");
            }
            return response;
        }
        catch (AiInvalidResponseException | AiProviderException | AiServiceUnavailableException exception) {
            throw exception;
        }
        catch (TransientAiException | NonTransientAiException | RestClientException exception) {
            throw new AiProviderException("AI Provider 当前不可用", exception);
        }
        catch (RuntimeException exception) {
            // Keep provider details out of logs and response bodies. The cause
            // remains attached for local diagnostics without exposing it in
            // the domain message.
            throw new AiProviderException("AI Provider 当前不可用", exception);
        }
    }

    private OpenAiChatOptions options(ToolCallback[] toolCallbacks, String toolChoice) {
        return OpenAiChatOptions.builder()
                .model(PRODUCTION_MODEL)
                .toolCallbacks(toolCallbacks)
                .toolChoice(toolChoice)
                .parallelToolCalls(false)
                .maxTokens(6000)
                .internalToolExecutionEnabled(false)
                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                .build();
    }

    private ChatModel productionModel() {
        ChatModel current = productionModel;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = productionModel;
            if (current == null) {
                if (!properties.isConfigured()) {
                    throw new AiServiceUnavailableException("AI 服务当前未启用");
                }
                current = buildProductionModel(properties.getApiKey());
                productionModel = current;
            }
            return current;
        }
    }

    private static ChatModel buildProductionModel(String apiKey) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(MODEL_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(MODEL_REQUEST_TIMEOUT);

        return buildModel(apiKey, org.springframework.web.client.RestClient.builder().requestFactory(requestFactory));
    }

    /** Same production model construction, with an HTTP test seam and no endpoint override. */
    static ChatModel buildModel(String apiKey, org.springframework.web.client.RestClient.Builder restClientBuilder) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(DEEPSEEK_BASE_URL)
                .completionsPath("/chat/completions")
                .apiKey(apiKey)
                .responseErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
                    @Override
                    public boolean hasError(org.springframework.http.client.ClientHttpResponse response)
                            throws java.io.IOException {
                        return response.getStatusCode().isError();
                    }

                    @Override
                    public void handleError(java.net.URI url, org.springframework.http.HttpMethod method,
                            org.springframework.http.client.ClientHttpResponse response) {
                        // Never read or log the provider error body, which may echo input data.
                        throw new AiProviderException("AI Provider 当前不可用");
                    }
                })
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(WebClient.builder())
                .build();

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new NeverRetryPolicy());
        ToolCallingManager manager = emptyToolCallingManager();
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(PRODUCTION_MODEL)
                .internalToolExecutionEnabled(false)
                .extraBody(Map.of("thinking", Map.of("type", "disabled")))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions)
                .toolCallingManager(manager)
                .retryTemplate(retryTemplate)
                .build();
    }

    private static ToolCallingManager emptyToolCallingManager() {
        return DefaultToolCallingManager.builder()
                .toolCallbackResolver(new StaticToolCallbackResolver(List.of()))
                .toolExecutionExceptionProcessor(new DefaultToolExecutionExceptionProcessor(true,
                        List.of(AiProviderException.class, AiServiceUnavailableException.class, AiInvalidResponseException.class)))
                .build();
    }

    private static AiInvalidResponseException invalid(Rule rule, String message) {
        return new AiInvalidResponseException(rule, message);
    }
}
