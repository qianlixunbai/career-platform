package com.careerplatform.ai.client;

import java.util.List;

/** Independent of Chat: all model and endpoint choices are server configuration. */
public interface EmbeddingGateway {
    boolean isAvailable();
    String identity();
    List<float[]> embed(List<String> texts);
}
