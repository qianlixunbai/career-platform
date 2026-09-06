package com.careerplatform.ai.tool;

import com.careerplatform.ai.client.JobSearchGateway;
import com.careerplatform.ai.service.JobSearchSession;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Objects;

/**
 * Request-local, read-only search tool exposed to the M6C model.
 *
 * <p>The instance owns no database mapper and is never registered as a
 * singleton tool.  A discovery request creates one with its own session.</p>
 */
public final class JobSearchTool {

    public static final int MAX_CALLS = 2;
    public static final int MAX_QUERY_LENGTH = 400;
    public static final int MAX_LOCATION_LENGTH = 100;
    public static final int MIN_RESULTS = 1;
    public static final int MAX_RESULTS = 10;

    private final JobSearchGateway gateway;
    private final JobSearchSession session;
    private int callCount;

    public JobSearchTool(JobSearchGateway gateway, JobSearchSession session) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    /** Search jobs through the configured provider and return opaque hits only. */
    @Tool(name = "searchJobs", description = "Search external job postings. Read-only; returns opaque result keys and provider snippets.")
    public synchronized List<JobSearchSession.SearchHit> searchJobs(
            String query,
            String location,
            int maxResults) {
        if (callCount >= MAX_CALLS) {
            throw new IllegalStateException("job search tool call budget exceeded");
        }
        // Count rejected attempts too.  A malformed model call cannot be used
        // to get an unbounded number of provider attempts.
        callCount++;

        String safeQuery = requireQuery(query);
        String safeLocation = normalizeLocation(location);
        if (maxResults < MIN_RESULTS || maxResults > MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and 10");
        }

        List<JobSearchGateway.ProviderSearchResult> providerResults =
                gateway.search(safeQuery, safeLocation, maxResults);
        return session.register(providerResults == null ? List.of()
                : providerResults.stream().limit(maxResults).toList());
    }

    /** Number of accepted or rejected tool invocation attempts, capped at 2. */
    public synchronized int getCallCount() {
        return callCount;
    }

    private static String requireQuery(String query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String normalized = query.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("query must contain between 1 and 400 characters");
        }
        return normalized;
    }

    private static String normalizeLocation(String location) {
        if (location == null) {
            return "";
        }
        String normalized = location.trim();
        if (normalized.length() > MAX_LOCATION_LENGTH) {
            throw new IllegalArgumentException("location must contain at most 100 characters");
        }
        return normalized;
    }
}
