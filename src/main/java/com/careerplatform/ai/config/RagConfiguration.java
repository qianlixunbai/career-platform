package com.careerplatform.ai.config;

import com.careerplatform.ai.client.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
public class RagConfiguration {
    @Bean @ConditionalOnMissingBean(EmbeddingGateway.class)
    EmbeddingGateway embeddingGateway(EmbeddingProperties properties) { return new OpenAiCompatibleEmbeddingGateway(properties); }
    @Bean @ConditionalOnMissingBean(RagChatGateway.class)
    RagChatGateway ragChatGateway(AiProperties properties) { return new SpringAiRagChatGateway(properties); }
}
