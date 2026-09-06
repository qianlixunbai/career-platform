package com.careerplatform.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Offline API spike against the resolved Spring AI 1.1.8 binaries. */
class ToolCallingCompatibilityTest {
    @Test
    void springAiExecutesExplicitCallbackAndBuildsToolResponseHistory() {
        SearchStub tool = new SearchStub();
        var callbacks = ToolCallbacks.from(tool);
        var options = OpenAiChatOptions.builder().model("deepseek-v4-flash")
                .internalToolExecutionEnabled(false).toolCallbacks(callbacks).toolChoice("auto")
                .extraBody(Map.of("thinking", Map.of("type", "disabled"))).build();
        Prompt prompt = new Prompt("Find a job", options);
        var message = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "searchJobs", "{\"query\":\"Java\"}"))).build();
        var response = new ChatResponse(List.of(new Generation(message)));
        var result = DefaultToolCallingManager.builder().build().executeToolCalls(prompt, response);
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("searchJobs");
        assertThat(result.conversationHistory().getLast()).isInstanceOf(ToolResponseMessage.class);
        assertThat(options.getExtraBody()).containsEntry("thinking", Map.of("type", "disabled"));
    }

    public static class SearchStub {
        final AtomicInteger calls = new AtomicInteger();
        @Tool(description = "Search jobs read-only")
        public String searchJobs(String query) { calls.incrementAndGet(); return "fixture-result-key"; }
    }
}
