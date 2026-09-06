package com.careerplatform.ai.tool;

import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.client.JobSearchGateway.ProviderSearchResult;
import com.careerplatform.ai.exception.AiProviderException;
import com.careerplatform.ai.service.JobSearchSession;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobSearchToolTest {

    @Test
    void exposesOnlySearchJobsAndCapsProviderResults() {
        RecordingGateway gateway = new RecordingGateway(List.of(
                result("one"), result("two"), result("three"), result("four")));
        JobSearchTool tool = new JobSearchTool(gateway, new JobSearchSession());

        var callbacks = ToolCallbacks.from(tool);
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks[0].getToolDefinition().name()).isEqualTo("searchJobs");

        List<JobSearchSession.SearchHit> hits = tool.searchJobs("  java  ", "  Shanghai  ", 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).sourceTitle()).isEqualTo("Posting one");
        assertThat(hits.get(1).sourceTitle()).isEqualTo("Posting two");
        assertThat(gateway.calls).hasValue(1);
        assertThat(gateway.lastQuery).isEqualTo("java");
        assertThat(gateway.lastLocation).isEqualTo("Shanghai");
        assertThat(gateway.lastMaxResults).isEqualTo(2);
        assertThat(tool.getCallCount()).isEqualTo(1);
    }

    @Test
    void invalidAttemptsConsumeTheTwoCallBudgetAndDoNotReachProvider() {
        RecordingGateway gateway = new RecordingGateway(List.of(result("one")));
        JobSearchTool tool = new JobSearchTool(gateway, new JobSearchSession());

        assertThatThrownBy(() -> tool.searchJobs("", "", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tool.getCallCount()).isEqualTo(1);

        assertThatThrownBy(() -> tool.searchJobs("java", "", 11))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tool.getCallCount()).isEqualTo(2);

        assertThatThrownBy(() -> tool.searchJobs("java", "", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("job search tool call budget exceeded");
        assertThat(tool.getCallCount()).isEqualTo(2);
        assertThat(gateway.calls).hasValue(0);
    }

    @Test
    void queryAndLocationBoundsAreValidatedBeforeProviderCall() {
        RecordingGateway gateway = new RecordingGateway(List.of(result("one")));
        JobSearchTool tool = new JobSearchTool(gateway, new JobSearchSession());

        assertThatThrownBy(() -> tool.searchJobs("q".repeat(401), "", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.searchJobs("java", "l".repeat(101), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tool.getCallCount()).isEqualTo(2);
        assertThat(gateway.calls).hasValue(0);
    }

    @Test
    void providerFailureStillConsumesAnAttempt() {
        RecordingGateway gateway = new RecordingGateway(List.of(result("one")));
        gateway.failure = new AiProviderException("sanitized provider failure");
        JobSearchTool tool = new JobSearchTool(gateway, new JobSearchSession());

        assertThatThrownBy(() -> tool.searchJobs("java", "", 1))
                .isSameAs(gateway.failure);
        assertThat(tool.getCallCount()).isEqualTo(1);
        assertThat(gateway.calls).hasValue(1);
    }

    @Test
    void concurrentInvocationsCannotExceedTwoProviderAttempts() throws Exception {
        RecordingGateway gateway = new RecordingGateway(List.of(result("one")), true);
        JobSearchTool tool = new JobSearchTool(gateway, new JobSearchSession());
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<JobSearchSession.SearchHit>>> futures = List.of(
                    executor.submit(() -> tool.searchJobs("java", "", 1)),
                    executor.submit(() -> tool.searchJobs("java", "", 1)),
                    executor.submit(() -> tool.searchJobs("java", "", 1)),
                    executor.submit(() -> tool.searchJobs("java", "", 1)));

            int successfulCalls = 0;
            int budgetFailures = 0;
            for (Future<List<JobSearchSession.SearchHit>> future : futures) {
                try {
                    assertThat(future.get()).hasSize(1);
                    successfulCalls++;
                }
                catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
                    budgetFailures++;
                }
            }
            assertThat(successfulCalls).isEqualTo(2);
            assertThat(budgetFailures).isEqualTo(2);
            assertThat(gateway.calls).hasValue(2);
            assertThat(tool.getCallCount()).isEqualTo(2);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static ProviderSearchResult result(String key) {
        return new ProviderSearchResult(
                "https://jobs.example.com/" + key,
                "Posting " + key,
                "provider.example",
                "Snippet " + key,
                null);
    }

    private static final class RecordingGateway implements JobSearchGateway {
        private final List<ProviderSearchResult> results;
        private final boolean uniqueResultPerCall;
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String lastQuery;
        private volatile String lastLocation;
        private volatile int lastMaxResults;
        private volatile RuntimeException failure;

        private RecordingGateway(List<ProviderSearchResult> results) {
            this(results, false);
        }

        private RecordingGateway(List<ProviderSearchResult> results, boolean uniqueResultPerCall) {
            this.results = results;
            this.uniqueResultPerCall = uniqueResultPerCall;
        }

        @Override
        public List<ProviderSearchResult> search(String query, String location, int maxResults) {
            int invocation = calls.incrementAndGet();
            lastQuery = query;
            lastLocation = location;
            lastMaxResults = maxResults;
            if (failure != null) {
                throw failure;
            }
            if (uniqueResultPerCall) {
                return List.of(result("attempt-" + invocation));
            }
            return results;
        }
    }
}
