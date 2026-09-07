package com.careerplatform.ai.client;

import com.careerplatform.ai.dto.rag.RagAiResult;

public interface RagChatGateway {
    boolean isAvailable();
    RagAiResult answer(String system, String content);
}
