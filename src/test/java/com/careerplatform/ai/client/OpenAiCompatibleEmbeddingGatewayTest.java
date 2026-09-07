package com.careerplatform.ai.client;

import com.careerplatform.ai.config.EmbeddingProperties;
import com.careerplatform.ai.exception.*;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class OpenAiCompatibleEmbeddingGatewayTest {
    private EmbeddingProperties configured() {
        var p = new EmbeddingProperties(); p.setEnabled(true); p.setApiKey("synthetic-test-key");
        p.setEndpoint("https://embedding.example.test/v1/embeddings"); p.setModel("fixture-v1"); return p;
    }
    private OpenAiCompatibleEmbeddingGateway gateway(String body) {
        return new OpenAiCompatibleEmbeddingGateway(configured(), (u,k,b) -> new OpenAiCompatibleEmbeddingGateway.Exchange(200, body.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void disabledHasNoNetworkAndIdentityChangesWithModelVersion() {
        var p = new EmbeddingProperties();
        var g = new OpenAiCompatibleEmbeddingGateway(p, (u,k,b) -> { throw new AssertionError("network"); });
        assertThat(g.isAvailable()).isFalse();
        assertThatThrownBy(() -> g.embed(List.of("x"))).isInstanceOf(AiServiceUnavailableException.class);
        String first = g.identity(); p.setVersion("2"); assertThat(g.identity()).isNotEqualTo(first);
    }
    @Test void ordersProviderIndicesAndSendsServerModel() {
        var g = new OpenAiCompatibleEmbeddingGateway(configured(), (uri,key,body) -> {
            assertThat(uri.toString()).isEqualTo("https://embedding.example.test/v1/embeddings");
            assertThat(body).contains("fixture-v1", "encoding_format", "float").doesNotContain(key);
            return new OpenAiCompatibleEmbeddingGateway.Exchange(200,
                    "{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}".getBytes(StandardCharsets.UTF_8));
        });
        var result = g.embed(List.of("Java", "SQL"));
        assertThat(result.getFirst()).containsExactly(1,0); assertThat(result.getLast()).containsExactly(0,1);
    }
    @Test void rejectsInvalidProviderVectorsIndicesAndJson() {
        for (String body : List.of("bad", "{}", "{\"data\":[]}",
                "{\"data\":[{\"index\":1,\"embedding\":[1]}]}",
                "{\"data\":[{\"index\":0,\"embedding\":[0]}]}",
                "{\"data\":[{\"index\":0,\"embedding\":[\"1\"]}]}"))
            assertThatThrownBy(() -> gateway(body).embed(List.of("x"))).isInstanceOf(AiProviderException.class).hasCause(null);
        assertThatThrownBy(() -> gateway("{\"data\":[{\"index\":0,\"embedding\":[1]},{\"index\":1,\"embedding\":[1,2]}]}")
                .embed(List.of("a","b"))).isInstanceOf(AiProviderException.class);
    }
    @Test void providerFailureIsSanitizedAndNotRetried() {
        var count = new AtomicInteger();
        var g = new OpenAiCompatibleEmbeddingGateway(configured(), (u,k,b) -> {
            count.incrementAndGet(); throw new RuntimeException("synthetic-private-provider-body");
        });
        assertThatThrownBy(() -> g.embed(List.of("x"))).isInstanceOf(AiProviderException.class)
                .hasCause(null).hasMessageNotContaining("synthetic-private");
        assertThat(count.get()).isEqualTo(1);
    }
    @Test void invalidLocalEndpointRemainsUnavailable() {
        var p = configured();
        for (String endpoint : List.of("http://example.test", "https://u:p@example.test/x", "https://example.test/x?key=x", "bad")) {
            p.setEndpoint(endpoint); assertThat(p.isConfigured()).isFalse();
        }
    }
}
