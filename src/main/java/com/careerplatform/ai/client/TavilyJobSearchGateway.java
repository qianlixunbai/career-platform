package com.careerplatform.ai.client;

import com.careerplatform.ai.config.JobSearchProperties;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * Tavily-backed read-only search gateway.
 *
 * <p>The production transport uses a JDK {@link HttpClient} with redirects
 * disabled and finite timeouts. The endpoint is an application constant; it
 * cannot be supplied by a request, environment variable, or configuration
 * property. A package-private transport seam supports deterministic tests.</p>
 */
public class TavilyJobSearchGateway implements JobSearchGateway {

    public static final URI SEARCH_ENDPOINT = URI.create("https://api.tavily.com/search");

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    static final int MAX_RESPONSE_BYTES = 1_048_576;

    private final JobSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpTransport transport;

    /** Create the production JDK HTTP implementation. */
    public TavilyJobSearchGateway(JobSearchProperties properties) {
        this(properties, new ObjectMapper(), defaultTransport());
    }

    /** Create production transport with the application-owned mapper. */
    public TavilyJobSearchGateway(JobSearchProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, defaultTransport());
    }

    /** Package-private deterministic transport seam. */
    TavilyJobSearchGateway(JobSearchProperties properties, ObjectMapper objectMapper, HttpTransport transport) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public void requireAvailable() {
        if (!properties.isConfigured()) {
            throw new AiServiceUnavailableException("职位搜索服务当前未启用");
        }
    }

    @Override
    public List<ProviderSearchResult> search(String query, String location, int maxResults) {
        requireAvailable();
        String safeQuery = validateQuery(query);
        String safeLocation = validateLocation(location);
        validateMaxResults(maxResults);

        String requestBody = serializeRequest(safeQuery, safeLocation, maxResults);
        HttpExchangeResponse response;
        try {
            response = transport.post(requestBody, properties.getApiKey());
        }
        catch (AiProviderException exception) {
            throw exception;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw providerUnavailable(exception);
        }
        catch (IOException exception) {
            throw providerUnavailable(exception);
        }
        catch (RuntimeException exception) {
            // Do not expose raw transport errors or provider bodies.
            throw providerUnavailable(exception);
        }

        if (response == null || response.statusCode() < 200 || response.statusCode() >= 300) {
            throw providerUnavailable(null);
        }
        byte[] body = response.body();
        if (body == null || body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            throw invalidProviderResponse(null);
        }

        try {
            return parseResponse(body, maxResults);
        }
        catch (AiProviderException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw invalidProviderResponse(exception);
        }
    }

    private String serializeRequest(String query, String location, int maxResults) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", location.isEmpty() ? query : query + " " + location);
        payload.put("search_depth", "basic");
        payload.put("topic", "general");
        payload.put("max_results", maxResults);
        payload.put("auto_parameters", false);
        payload.put("include_answer", false);
        payload.put("include_raw_content", false);
        payload.put("include_images", false);
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException exception) {
            throw invalidProviderResponse(exception);
        }
    }

    private List<ProviderSearchResult> parseResponse(byte[] body, int maxResults) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(body);
        }
        catch (IOException exception) {
            throw invalidProviderResponse(exception);
        }
        if (root == null || !root.isObject() || !root.has("results") || !root.get("results").isArray()) {
            throw invalidProviderResponse(null);
        }

        List<ProviderSearchResult> results = new ArrayList<>();
        for (JsonNode result : root.get("results")) {
            if (result == null || !result.isObject()) {
                continue;
            }
            String sourceUrl = text(result, "url");
            String sourceTitle = text(result, "title");
            String sourceSnippet = text(result, "content");
            String sourceHost = hostOf(sourceUrl);
            LocalDate publishedAt = explicitPublishedDate(result);
            results.add(new ProviderSearchResult(
                    sourceUrl,
                    sourceTitle,
                    sourceHost,
                    sourceSnippet,
                    publishedAt));
            if (results.size() >= maxResults) {
                break;
            }
        }
        return List.copyOf(results);
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        return value.asText();
    }

    private static LocalDate explicitPublishedDate(JsonNode result) {
        // Tavily's general-search result commonly omits this field. Only an
        // explicit provider field is considered; no date is inferred from
        // snippets, titles, or the current clock.
        String value = text(result, "published_date");
        if (value == null) {
            value = text(result, "publishedDate");
        }
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        }
        catch (DateTimeParseException ignored) {
            // Some provider-compatible responses use an explicit timestamp.
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        }
        catch (DateTimeParseException ignored) {
            // Continue with the other standard ISO timestamp representations.
        }
        try {
            return ZonedDateTime.parse(value).toLocalDate();
        }
        catch (DateTimeParseException ignored) {
            // Continue with an instant when the provider includes a Z suffix.
        }
        try {
            return Instant.parse(value).atZone(java.time.ZoneOffset.UTC).toLocalDate();
        }
        catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String hostOf(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(sourceUrl);
            return uri.getHost();
        }
        catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String validateQuery(String query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String normalized = query.trim();
        if (normalized.isEmpty() || normalized.length() > 400) {
            throw new IllegalArgumentException("query must contain between 1 and 400 characters");
        }
        return normalized;
    }

    private static String validateLocation(String location) {
        if (location == null) {
            return "";
        }
        String normalized = location.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("location must contain at most 100 characters");
        }
        return normalized;
    }

    private static void validateMaxResults(int maxResults) {
        if (maxResults < 1 || maxResults > 10) {
            throw new IllegalArgumentException("maxResults must be between 1 and 10");
        }
    }

    private static HttpTransport defaultTransport() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        return new JdkHttpTransport(httpClient);
    }

    private static AiProviderException providerUnavailable(Throwable cause) {
        return cause == null
                ? new AiProviderException("职位搜索 Provider 当前不可用")
                : new AiProviderException("职位搜索 Provider 当前不可用", cause);
    }

    private static AiProviderException invalidProviderResponse(Throwable cause) {
        return cause == null
                ? new AiProviderException("职位搜索 Provider 返回内容无效")
                : new AiProviderException("职位搜索 Provider 返回内容无效", cause);
    }

    @FunctionalInterface
    interface HttpTransport {
        HttpExchangeResponse post(String body, String apiKey) throws IOException, InterruptedException;
    }

    record HttpExchangeResponse(int statusCode, byte[] body) {
    }

    static final class JdkHttpTransport implements HttpTransport {
        private final HttpClient httpClient;
        private final Duration timeout;

        private JdkHttpTransport(HttpClient httpClient) {
            this(httpClient, REQUEST_TIMEOUT);
        }

        JdkHttpTransport(HttpClient httpClient, Duration timeout) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            this.timeout = timeout;
        }

        @Override
        public HttpExchangeResponse post(String body, String apiKey) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(SEARCH_ENDPOINT)
                    .timeout(timeout)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var exchange = httpClient.sendAsync(request, boundedBodyHandler());
            try {
                // The request timeout bounds the complete asynchronous
                // exchange, including a provider that sends headers and then
                // stalls while streaming its body. The subscriber below adds
                // a hard byte limit and cancels the subscription early.
                HttpResponse<byte[]> response = exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return new HttpExchangeResponse(response.statusCode(), response.body());
            }
            catch (TimeoutException exception) {
                exchange.cancel(true);
                throw new java.net.http.HttpTimeoutException("Tavily response deadline exceeded");
            }
            catch (InterruptedException exception) {
                exchange.cancel(true);
                throw exception;
            }
            catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                if (cause instanceof InterruptedException interruptedException) {
                    throw interruptedException;
                }
                throw new IOException("Tavily request failed", cause);
            }
        }
    }

    static HttpResponse.BodyHandler<byte[]> boundedBodyHandler() {
        return ignored -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES);
    }

    /** Body subscriber that cancels a provider stream as soon as it exceeds the limit. */
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate;
        private final int maximumBytes;
        private int receivedBytes;
        private Flow.Subscription subscription;
        private boolean failed;

        private BoundedBodySubscriber(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            this.delegate = HttpResponse.BodySubscribers.ofByteArray();
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (failed) {
                return;
            }
            long incoming = 0;
            for (ByteBuffer item : items) {
                incoming += item.remaining();
            }
            if (incoming > maximumBytes - receivedBytes) {
                failed = true;
                if (subscription != null) {
                    subscription.cancel();
                }
                delegate.onError(new IOException("response body exceeds configured limit"));
                return;
            }
            receivedBytes += (int) incoming;
            delegate.onNext(items);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!failed) {
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!failed) {
                delegate.onComplete();
            }
        }
    }
}
