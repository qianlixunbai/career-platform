package com.careerplatform.ai;

import com.careerplatform.ai.client.AiChatGateway;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "career-platform.ai.enabled=true",
        "career-platform.ai.provider=openai",
        "career-platform.ai.api-key=",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=not-configured"
})
class AiOpenAiWithoutKeyContextTest {
    @Autowired
    private AiChatGateway aiChatGateway;

    @Test
    void selectedProviderWithoutRealApiKeyStillStartsAndFailsClosedAtGateway() {
        assertThatThrownBy(() -> aiChatGateway.generateStructured("system", "user", String.class))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI 服务当前未启用");
    }
}
