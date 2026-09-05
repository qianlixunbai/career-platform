package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

import java.util.Objects;

/** Spring AI-backed implementation of the provider-neutral gateway. */
public class SpringAiChatGateway implements AiChatGateway {
    private static final Logger log = LoggerFactory.getLogger(SpringAiChatGateway.class);
    static final String PRODUCTION_MODEL = "deepseek-v4-flash";
    private static final OpenAiChatOptions FIXED_CHAT_OPTIONS = OpenAiChatOptions.builder()
            .model(PRODUCTION_MODEL)
            .toolChoice("none")
            .internalToolExecutionEnabled(false)
            .build();

    private final AiProperties properties;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;

    public SpringAiChatGateway(
            AiProperties properties,
            ObjectProvider<ChatClient> chatClientProvider,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectProvider<ChatModel> chatModelProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.chatClientProvider = Objects.requireNonNull(chatClientProvider, "chatClientProvider must not be null");
        this.chatClientBuilderProvider = Objects.requireNonNull(
                chatClientBuilderProvider, "chatClientBuilderProvider must not be null");
        this.chatModelProvider = Objects.requireNonNull(chatModelProvider, "chatModelProvider must not be null");
    }

    @Override
    public <T> T generateStructured(String systemInstruction, String userContent, Class<T> responseType) {
        if (!properties.isConfigured()) {
            log.info("AI request rejected because chat AI is disabled or provider configuration is incomplete");
            throw new AiServiceUnavailableException("AI 服务当前未启用");
        }
        Objects.requireNonNull(systemInstruction, "systemInstruction must not be null");
        Objects.requireNonNull(userContent, "userContent must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");

        ChatClient chatClient = resolveChatClient();
        try {
            T entity = chatClient.prompt()
                    .system(systemInstruction)
                    .user(userContent)
                    .options(FIXED_CHAT_OPTIONS)
                    .call()
                    .entity(responseType);
            if (entity == null) {
                throw new AiInvalidResponseException("AI 服务返回了空的结构化结果");
            }
            return entity;
        } catch (AiInvalidResponseException exception) {
            throw exception;
        } catch (TransientAiException | NonTransientAiException | ResourceAccessException exception) {
            log.warn("AI provider request failed: {}", exception.getClass().getSimpleName());
            throw new AiProviderException("AI Provider 当前不可用", exception);
        } catch (RuntimeException exception) {
            log.warn("AI structured output validation failed: {}", exception.getClass().getSimpleName());
            throw new AiInvalidResponseException("AI 返回内容不符合结构化格式", exception);
        }
    }

    private ChatClient resolveChatClient() {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient != null) {
            return chatClient;
        }

        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder != null) {
            try {
                return builder.build();
            } catch (RuntimeException exception) {
                throw new AiProviderException("AI chat client could not be initialized", exception);
            }
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            return ChatClient.create(chatModel);
        }

        throw new AiServiceUnavailableException(
                "AI 服务当前未启用");
    }
}
