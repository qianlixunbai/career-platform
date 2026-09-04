package com.careerplatform.ai.config;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.client.SpringAiChatGateway;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI foundation wiring. The gateway remains available while disabled so
 * callers receive a deterministic domain exception instead of a missing bean.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiFoundationConfiguration {

    /**
     * Spring AI's OpenAI ChatModel requires this infrastructure bean even
     * when the application exposes no tools. The default resolver below is
     * intentionally empty, so the model cannot discover or invoke a tool.
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallingManager.class)
    ToolCallingManager emptyToolCallingManager() {
        return DefaultToolCallingManager.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean(AiChatGateway.class)
    AiChatGateway aiChatGateway(
            AiProperties properties,
            ObjectProvider<ChatClient> chatClientProvider,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider,
            ObjectProvider<ChatModel> chatModelProvider) {
        return new SpringAiChatGateway(
                properties,
                chatClientProvider,
                chatClientBuilderProvider,
                chatModelProvider);
    }

    /**
     * Build a ChatClient when the application switch and a provider model are
     * both available. The gateway also supports Spring AI's prototype builder
     * directly, so this bean is deliberately conditional and lazy by design.
     */
    @Bean
    @ConditionalOnProperty(prefix = "career-platform.ai", name = "enabled", havingValue = "true")
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(ChatClient.class)
    ChatClient careerPlatformChatClient(
            ChatModel chatModel,
            ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        return builder == null ? ChatClient.create(chatModel) : builder.build();
    }
}
