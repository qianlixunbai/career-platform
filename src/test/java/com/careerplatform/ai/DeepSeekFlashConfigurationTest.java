package com.careerplatform.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekFlashConfigurationTest {

    @Test
    void productionConfigurationIsDeepSeekFlashOnly() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties)
                .contains("spring.ai.openai.base-url=https://api.deepseek.com")
                .contains("spring.ai.openai.chat.options.model=deepseek-v4-flash")
                .doesNotContain("AI_MODEL")
                .doesNotContain("deepseek-v4-pro");
    }
}
