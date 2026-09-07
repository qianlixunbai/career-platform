package com.careerplatform.ai.client;

import com.careerplatform.ai.config.AiProperties;
import com.careerplatform.ai.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class SpringAiRagChatGatewayTest {
    private AiProperties properties() { var p = new AiProperties(); p.setEnabled(true); p.setProvider("openai"); p.setApiKey("synthetic"); return p; }
    private ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    @Test void fixedFlashNoToolsStructuredParsing() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenAnswer(inv -> {
            org.springframework.ai.chat.prompt.Prompt prompt = inv.getArgument(0);
            var options = (OpenAiChatOptions) prompt.getOptions();
            assertThat(options.getModel()).isEqualTo("deepseek-v4-flash");
            assertThat(options.getToolChoice()).isEqualTo("none");
            assertThat(options.getInternalToolExecutionEnabled()).isFalse();
            assertThat(options.getToolCallbacks()).isEmpty();
            return response("```json\n{\"answer\":\"Java 21\",\"citationKeys\":[\"c1\"],\"evidenceInsufficient\":false}\n```");
        });
        var result = new SpringAiRagChatGateway(properties(), model).answer("system", "data");
        assertThat(result.citationKeys()).containsExactly("c1"); verify(model, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }
    @Test void invalidJsonUnknownFieldsAndCoercionNeverExposeRawData() {
        for (String value : List.of("synthetic-private-document", "null", "{} {}",
                "{\"answer\":\"x\",\"pageNumber\":12}", "{\"answer\":42}")) {
            ChatModel model = mock(ChatModel.class); when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response(value));
            assertThatThrownBy(() -> new SpringAiRagChatGateway(properties(), model).answer("s","u"))
                    .isInstanceOf(AiInvalidResponseException.class).hasCause(null).hasMessageNotContaining("synthetic-private");
        }
    }
    @Test void unavailableAndProviderFailureAreSafe() {
        ChatModel model = mock(ChatModel.class);
        assertThatThrownBy(() -> new SpringAiRagChatGateway(new AiProperties(), model).answer("s","u"))
                .isInstanceOf(AiServiceUnavailableException.class); verifyNoInteractions(model);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenThrow(new RuntimeException("private"));
        assertThatThrownBy(() -> new SpringAiRagChatGateway(properties(), model).answer("s","u"))
                .isInstanceOf(AiProviderException.class).hasCause(null).hasMessageNotContaining("private");
    }
}
