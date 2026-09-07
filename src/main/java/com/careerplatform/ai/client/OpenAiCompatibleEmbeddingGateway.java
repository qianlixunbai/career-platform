package com.careerplatform.ai.client;

import com.careerplatform.ai.config.EmbeddingProperties;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.careerplatform.ai.service.EmbeddingVectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** OpenAI-compatible embedding wire protocol, bounded and without SDK retries. */
public class OpenAiCompatibleEmbeddingGateway implements EmbeddingGateway {
    private final EmbeddingProperties properties;
    private final ObjectMapper json = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final Transport transport;

    public OpenAiCompatibleEmbeddingGateway(EmbeddingProperties properties) {
        this(properties, productionTransport());
    }
    OpenAiCompatibleEmbeddingGateway(EmbeddingProperties properties, Transport transport) {
        this.properties = properties; this.transport = transport;
    }
    @Override public boolean isAvailable() { return properties.isConfigured(); }
    @Override public String identity() {
        try {
            String spec = properties.getEndpoint() + "\n" + properties.getModel() + "\n" + properties.getVersion();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(spec.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) { throw failure(); }
    }
    @Override public List<float[]> embed(List<String> texts) {
        if (!isAvailable()) throw new AiServiceUnavailableException("学习资料向量服务当前未配置");
        if (texts == null || texts.isEmpty() || texts.size() > 128
                || texts.stream().anyMatch(t -> t == null || t.isBlank() || t.length() > 1000)) throw failure();
        List<float[]> vectors = new ArrayList<>();
        int dimension = -1;
        try {
            for (int start = 0; start < texts.size(); start += 16) {
                var batch = texts.subList(start, Math.min(texts.size(), start + 16));
                String body = json.writeValueAsString(Map.of("model", properties.getModel(), "input", batch, "encoding_format", "float"));
                Exchange response = transport.post(URI.create(properties.getEndpoint()), properties.getApiKey(), body);
                if (response.status() != 200 || response.body() == null || response.body().length > 1_048_576) throw failure();
                var root = json.readTree(response.body());
                var data = root.path("data");
                if (!data.isArray() || data.size() != batch.size()) throw failure();
                float[][] ordered = new float[batch.size()][];
                for (var item : data) {
                    var index = item.path("index");
                    if (!index.isIntegralNumber() || !index.canConvertToInt()) throw failure();
                    int i = index.intValue();
                    if (i < 0 || i >= ordered.length || ordered[i] != null) throw failure();
                    float[] vector = EmbeddingVectors.decode(item.path("embedding").toString());
                    if (dimension == -1) dimension = vector.length;
                    if (vector.length != dimension) throw failure();
                    ordered[i] = vector;
                }
                vectors.addAll(Arrays.asList(ordered));
            }
            return List.copyOf(vectors);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt(); throw failure();
        } catch (Exception ignored) { throw failure(); }
    }

    interface Transport { Exchange post(URI endpoint, String key, String body) throws Exception; }
    record Exchange(int status, byte[] body) { }
    private static Transport productionTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return (endpoint, key, body) -> {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            // Reuse the existing 1 MiB bounded subscriber; no search request or tool is invoked.
            var future = client.sendAsync(request, TavilyJobSearchGateway.boundedBodyHandler());
            try {
                var response = future.get(30, TimeUnit.SECONDS);
                return new Exchange(response.statusCode(), response.body());
            } catch (Exception exception) { future.cancel(true); throw exception; }
        };
    }
    private static AiProviderException failure() { return new AiProviderException("学习资料向量服务暂时不可用"); }
}
