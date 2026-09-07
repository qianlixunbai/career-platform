package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.dto.rag.RagAiResult;
import com.careerplatform.ai.exception.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.*;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/** Independent no-tools model instance. No global ChatClient/default callbacks. */
public class SpringAiRagChatGateway implements RagChatGateway {
    private final AiProperties properties;
    private final Supplier<ChatModel> model;
    private volatile ChatModel production;
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    public SpringAiRagChatGateway(AiProperties properties) {
        this.properties = properties; this.model = this::productionModel;
    }
    SpringAiRagChatGateway(AiProperties properties, ChatModel model) {
        this.properties = properties; this.model = () -> model;
    }
    @Override public boolean isAvailable() { return properties.isConfigured(); }
    @Override public RagAiResult answer(String system, String content) {
        if (!isAvailable()) throw new AiServiceUnavailableException("AI 服务当前未启用");
        var schema = new BeanOutputConverter<>(RagAiResult.class, json);
        var options = OpenAiChatOptions.builder().model("deepseek-v4-flash")
                .toolChoice("none").toolCallbacks(List.of()).internalToolExecutionEnabled(false)
                .maxTokens(2000).extraBody(Map.of("thinking", Map.of("type", "disabled"))).build();
        final String text;
        try {
            var response = model.get().call(new Prompt(List.of(new SystemMessage(system + "\n" + schema.getFormat()),
                    new UserMessage(content)), options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                    || response.getResult().getOutput().hasToolCalls()) throw invalid();
            text = response.getResult().getOutput().getText();
        } catch (AiInvalidResponseException exception) { throw exception; }
        catch (RuntimeException ignored) { throw new AiProviderException("AI Provider 当前不可用"); }
        if (text == null || text.isBlank() || text.length() > 16000) throw invalid();
        try {
            // Schema-only BeanOutputConverter: its parsing path logs untrusted raw text on failure.
            var cleaner = CompositeResponseTextCleaner.builder().addCleaner(new WhitespaceCleaner())
                    .addCleaner(new ThinkingTagCleaner()).addCleaner(new MarkdownCodeBlockCleaner())
                    .addCleaner(new WhitespaceCleaner()).build();
            var root = json.readTree(cleaner.clean(text));
            if (root == null || !root.isObject() || root.size() != 3 || !root.path("answer").isTextual()
                    || !root.path("citationKeys").isArray() || !root.path("evidenceInsufficient").isBoolean()) throw invalid();
            for (var key : root.path("citationKeys")) if (!key.isTextual()) throw invalid();
            RagAiResult result = json.treeToValue(root, RagAiResult.class);
            if (result == null) throw invalid();
            return result;
        } catch (Exception ignored) { throw invalid(); }
    }
    private synchronized ChatModel productionModel() {
        if (production == null) {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            var factory = new JdkClientHttpRequestFactory(client);
            factory.setReadTimeout(Duration.ofSeconds(40));
            // Existing fixed-endpoint factory builds an isolated Flash model with retry=0 and an empty resolver.
            production = SpringAiToolCallingGateway.buildModel(properties.getApiKey(), RestClient.builder().requestFactory(factory));
        }
        return production;
    }
    private static AiInvalidResponseException invalid() { return new AiInvalidResponseException("AI 返回内容无法安全处理"); }
}
