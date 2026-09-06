package com.careerplatform.ai.client;

import com.careerplatform.ai.config.JobSearchProperties;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.exception.AiServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TavilyJobSearchGatewayTest {

    @Test
    void disabledProviderFailsBeforeTransport() {
        JobSearchProperties properties = new JobSearchProperties();
        AtomicInteger calls = new AtomicInteger();
        TavilyJobSearchGateway gateway = new TavilyJobSearchGateway(
                properties,
                new ObjectMapper(),
                (body, apiKey) -> {
                    calls.incrementAndGet();
                    return new TavilyJobSearchGateway.HttpExchangeResponse(200, "{}".getBytes());
                });

        assertThatThrownBy(() -> gateway.search("java", "Shanghai", 3))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessage("职位搜索服务当前未启用");
        assertThat(calls).hasValue(0);
    }

    @Test
    void requestUsesFixedSafeBasicGeneralSearchOptionsAndParsesExplicitDatesOnly() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String response = """
                {
                  "results": [
                    {"title":"Java role","url":"https://jobs.example.com/openings/java?id=1","content":"Java snippet","published_date":"2026-09-01"},
                    {"title":"No date","url":"https://jobs.example.com/openings/spring","content":"Spring snippet"},
                    {"title":"Timestamp","url":"https://jobs.example.com/openings/kotlin","content":"Kotlin snippet","published_date":"2026-09-02T10:11:12Z"},
                    {"title":"Bad date","url":"https://jobs.example.com/openings/bad","content":"Bad snippet","published_date":"not-a-date"}
                  ]
                }
                """;
        TavilyJobSearchGateway gateway = gateway((body, apiKey) -> {
            requestBody.set(body);
            assertThat(apiKey).isEqualTo("test-tavily-key");
            return new TavilyJobSearchGateway.HttpExchangeResponse(200, response.getBytes());
        });

        List<JobSearchGateway.ProviderSearchResult> results =
                gateway.search("  java  ", "  Shanghai  ", 4);

        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertThat(request.has("api_key")).isFalse();
        assertThat(request.get("query").asText()).isEqualTo("java Shanghai");
        assertThat(request.get("search_depth").asText()).isEqualTo("basic");
        assertThat(request.get("topic").asText()).isEqualTo("general");
        assertThat(request.get("max_results").asInt()).isEqualTo(4);
        assertThat(request.get("auto_parameters").asBoolean()).isFalse();
        assertThat(request.get("include_answer").asBoolean()).isFalse();
        assertThat(request.get("include_raw_content").asBoolean()).isFalse();
        assertThat(request.get("include_images").asBoolean()).isFalse();

        assertThat(results).hasSize(4);
        assertThat(results.get(0).sourceUrl()).isEqualTo("https://jobs.example.com/openings/java?id=1");
        assertThat(results.get(0).sourceTitle()).isEqualTo("Java role");
        assertThat(results.get(0).sourceHost()).isEqualTo("jobs.example.com");
        assertThat(results.get(0).sourceSnippet()).isEqualTo("Java snippet");
        assertThat(results.get(0).publishedAt()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(results.get(1).publishedAt()).isNull();
        assertThat(results.get(2).publishedAt()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(results.get(3).publishedAt()).isNull();
    }

    @Test
    void malformedProviderBodyIsSanitizedAsProviderError() {
        TavilyJobSearchGateway gateway = gateway((body, apiKey) ->
                new TavilyJobSearchGateway.HttpExchangeResponse(
                        200, "{malformed provider body with secret-marker}".getBytes()));

        assertThatThrownBy(() -> gateway.search("java", "", 1))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("职位搜索 Provider 返回内容无效")
                .hasMessageNotContaining("secret-marker");
    }

    @Test
    void nonSuccessfulProviderResponsesAreSanitized() {
        for (int statusCode : List.of(400, 401, 500, 503)) {
            TavilyJobSearchGateway gateway = gateway((body, apiKey) ->
                    new TavilyJobSearchGateway.HttpExchangeResponse(
                            statusCode, "provider secret-marker body".getBytes()));

            assertThatThrownBy(() -> gateway.search("java", "", 1))
                    .isInstanceOf(AiProviderException.class)
                    .hasMessage("职位搜索 Provider 当前不可用")
                    .hasMessageNotContaining("secret-marker");
        }
    }

    @Test
    void oversizedProviderBodyIsRejectedBeforeParsing() {
        byte[] oversized = new byte[TavilyJobSearchGateway.MAX_RESPONSE_BYTES + 1];
        TavilyJobSearchGateway gateway = gateway((body, apiKey) ->
                new TavilyJobSearchGateway.HttpExchangeResponse(200, oversized));

        assertThatThrownBy(() -> gateway.search("java", "", 1))
                .isInstanceOf(AiProviderException.class)
                .hasMessage("职位搜索 Provider 返回内容无效");
    }

    @Test
    void jdkTransportCancelsWholeExchangeAfterDeadline() {
        HangingHttpClient client = new HangingHttpClient();
        TavilyJobSearchGateway.JdkHttpTransport transport =
                new TavilyJobSearchGateway.JdkHttpTransport(client, Duration.ofMillis(25));

        assertThatThrownBy(() -> transport.post("{}", "test-tavily-key"))
                .isInstanceOf(java.net.http.HttpTimeoutException.class)
                .hasMessage("Tavily response deadline exceeded");
        assertThat(client.exchange.isCancelled()).isTrue();
        assertThat(client.lastRequest.uri()).isEqualTo(TavilyJobSearchGateway.SEARCH_ENDPOINT);
        assertThat(client.lastRequest.headers().firstValue("Authorization"))
                .contains("Bearer test-tavily-key");
    }

    @Test
    void boundedBodySubscriberCancelsWhenProviderExceedsLimit() {
        HttpResponse.BodySubscriber<byte[]> subscriber =
                TavilyJobSearchGateway.boundedBodyHandler().apply(null);
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);

        subscriber.onNext(List.of(ByteBuffer.allocate(TavilyJobSearchGateway.MAX_RESPONSE_BYTES + 1)));

        assertThat(subscription.cancelled).isTrue();
        assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    private static TavilyJobSearchGateway gateway(TavilyJobSearchGateway.HttpTransport transport) {
        JobSearchProperties properties = new JobSearchProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-tavily-key");
        return new TavilyJobSearchGateway(properties, new ObjectMapper(), transport);
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private boolean cancelled;

        @Override
        public void request(long count) {
            // The delegate requests data, but this deterministic test supplies it directly.
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final class HangingHttpClient extends HttpClient {
        private final CompletableFuture<HttpResponse<byte[]>> exchange = new CompletableFuture<>();
        private volatile HttpRequest lastRequest;

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            }
            catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException("send should not be used by this transport");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            lastRequest = request;
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) exchange;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            lastRequest = request;
            return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>) exchange;
        }
    }
}
