package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.dto.JdParseAiResult;
import com.careerplatform.ai.exception.AiInvalidResponseException;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class SpringAiChatGatewayTest {

    @Mock
    private ObjectProvider<ChatClient> chatClientProvider;

    @Mock
    private ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Test
    void disabledAiFailsClearlyWithoutCallingProvider() {
        AiProperties properties = new AiProperties();
        SpringAiChatGateway gateway = gateway(properties);

        assertThatThrownBy(() -> gateway.generateStructured("system", "user", String.class))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("AI 服务当前未启用");
    }

    @Test
    void delegatesPromptAndParsesTypedEntity() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setApiKey("test-key");
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.options(any(OpenAiChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(String.class)).thenReturn("structured");

        String result = gateway(properties).generateStructured("system", "user", String.class);

        assertThat(result).isEqualTo("structured");
        verify(requestSpec).system("system");
        verify(requestSpec).user("user");
        ArgumentCaptor<OpenAiChatOptions> options = ArgumentCaptor.forClass(OpenAiChatOptions.class);
        verify(requestSpec).options(options.capture());
        assertThat(options.getValue().getModel()).isEqualTo("deepseek-v4-flash");
        assertThat(options.getValue().getToolChoice()).isEqualTo("none");
        assertThat(options.getValue().getInternalToolExecutionEnabled()).isFalse();
    }

    @Test
    void parseFailureIsNormalized() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setApiKey("test-key");
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.options(any(OpenAiChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(String.class)).thenThrow(new IllegalStateException("invalid json"));

        assertThatThrownBy(() -> gateway(properties).generateStructured("system", "user", String.class))
                .isInstanceOf(AiInvalidResponseException.class)
                .hasMessage("AI 返回内容不符合结构化格式");
    }

    @Test
    void networkFailureIsNormalizedAsProviderUnavailable() {
        AiProperties properties = configuredProperties();
        when(chatClientProvider.getIfAvailable()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.user("user")).thenReturn(requestSpec);
        when(requestSpec.options(any(OpenAiChatOptions.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.entity(String.class)).thenThrow(new ResourceAccessException("provider timeout"));

        assertThatThrownBy(() -> gateway(properties).generateStructured("system", "user", String.class))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("AI Provider 当前不可用");
    }

    @Test
    void realSpringAiChatClientConvertsFakeModelJsonToTypedResult() {
        AiProperties properties = configuredProperties();
        DeterministicChatModel fakeModel = new DeterministicChatModel(
                "{\"requirements\":[],\"warnings\":[\"deterministic fake\"]}");
        when(chatClientProvider.getIfAvailable()).thenReturn(ChatClient.create(fakeModel));

        JdParseAiResult result = gateway(properties)
                .generateStructured("system instruction", "untrusted JD", JdParseAiResult.class);

        assertThat(result.getRequirements()).isEmpty();
        assertThat(result.getWarnings()).containsExactly("deterministic fake");
        assertThat(fakeModel.lastPrompt.getSystemMessage().getText()).isEqualTo("system instruction");
        assertThat(fakeModel.lastPrompt.getUserMessage().getText()).startsWith("untrusted JD");
        assertThat(fakeModel.lastPrompt.getOptions()).isInstanceOfSatisfying(OpenAiChatOptions.class, options -> {
            assertThat(options.getModel()).isEqualTo("deepseek-v4-flash");
            assertThat(options.getToolChoice()).isEqualTo("none");
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        });
    }

    private AiProperties configuredProperties() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("openai");
        properties.setApiKey("test-key");
        return properties;
    }

    private static final class DeterministicChatModel implements ChatModel {
        private final String responseJson;
        private Prompt lastPrompt;

        private DeterministicChatModel(String responseJson) {
            this.responseJson = responseJson;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(responseJson))));
        }
    }

    private SpringAiChatGateway gateway(AiProperties properties) {
        return new SpringAiChatGateway(
                properties,
                chatClientProvider,
                chatClientBuilderProvider,
                chatModelProvider);
    }
}
