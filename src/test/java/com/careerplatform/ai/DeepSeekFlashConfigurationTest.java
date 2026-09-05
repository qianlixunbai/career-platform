package com.careerplatform.ai;

import com.careerplatform.ai.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class DeepSeekFlashConfigurationTest {

    @Test
    void productionConfigurationIsDeepSeekFlashOnly() throws IOException {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties)
                .contains("career-platform.ai.enabled=${AI_CHAT_ENABLED:${AI_JD_PARSE_ENABLED:false}}")
                .contains("spring.ai.openai.base-url=https://api.deepseek.com")
                .contains("spring.ai.openai.chat.options.model=deepseek-v4-flash")
                .doesNotContain("AI_MODEL")
                .doesNotContain("deepseek-v4-pro");
    }

    @Test
    void globalSwitchUsesProductionPropertiesAndLegacyOnlyAsFallback() throws IOException {
        assertEnabled(Map.of(), false);
        assertEnabled(Map.of("AI_JD_PARSE_ENABLED", "true"), true);
        assertEnabled(Map.of("AI_JD_PARSE_ENABLED", "false"), false);
        assertEnabled(Map.of("AI_CHAT_ENABLED", "true", "AI_JD_PARSE_ENABLED", "false"), true);
        assertEnabled(Map.of("AI_CHAT_ENABLED", "false", "AI_JD_PARSE_ENABLED", "true"), false);
    }

    private void assertEnabled(Map<String, String> environment, boolean expected) throws IOException {
        Properties productionProperties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            productionProperties.load(reader);
        }

        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withUserConfiguration(AiPropertiesBindingConfiguration.class)
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(
                            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().addLast(
                            new PropertiesPropertySource("production-application", productionProperties));
                });
        String[] values = environment.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        contextRunner.withPropertyValues(values).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiProperties.class).isEnabled()).isEqualTo(expected);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    static class AiPropertiesBindingConfiguration {
    }
}
