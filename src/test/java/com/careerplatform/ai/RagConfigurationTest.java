package com.careerplatform.ai;

import com.careerplatform.ai.client.*;
import com.careerplatform.ai.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RagConfigurationTest {
    @Test void noAiConfigurationStartsAndGatewaysRemainUnavailable() {
        new ApplicationContextRunner().withUserConfiguration(RagConfiguration.class)
                .withBean(AiProperties.class, AiProperties::new).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmbeddingGateway.class).isAvailable()).isFalse();
                    assertThat(context.getBean(RagChatGateway.class).isAvailable()).isFalse();
                    assertThat(context.getBeansOfType(org.springframework.ai.chat.model.ChatModel.class)).isEmpty();
                    assertThat(context.getBeansOfType(org.springframework.ai.tool.ToolCallback.class)).isEmpty();
                });
    }
    @Test void embeddingConfigurationDoesNotEnableChatOrGlobalModels() {
        new ApplicationContextRunner().withUserConfiguration(RagConfiguration.class)
                .withBean(AiProperties.class, AiProperties::new)
                .withPropertyValues("career-platform.embedding.enabled=true",
                        "career-platform.embedding.endpoint=https://embedding.example.test/v1/embeddings",
                        "career-platform.embedding.model=fixture", "career-platform.embedding.api-key=synthetic")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmbeddingGateway.class).isAvailable()).isTrue();
                    assertThat(context.getBean(RagChatGateway.class).isAvailable()).isFalse();
                    assertThat(context.getBeansOfType(org.springframework.ai.embedding.EmbeddingModel.class)).isEmpty();
                });
    }
}
