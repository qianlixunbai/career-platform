package com.careerplatform.ai.client;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only boundary for an external job-search provider.
 *
 * <p>The gateway deliberately exposes provider facts only.  Discovery code
 * owns the ephemeral session and the later candidate reconstruction.</p>
 */
public interface JobSearchGateway {

    /**
     * Fail closed when the provider is disabled or has no local credential.
     * Fakes may keep the default implementation so deterministic tests do not
     * need provider configuration.
     */
    default void requireAvailable() {
        // Intentionally empty for test doubles and local implementations.
    }

    /**
     * Search an external provider without performing any application writes.
     *
     * @param query bounded natural-language job query
     * @param location bounded optional location text
     * @param maxResults bounded result count
     * @return provider facts, never application entities
     */
    List<ProviderSearchResult> search(String query, String location, int maxResults);

    /** Facts returned by a search provider. */
    record ProviderSearchResult(
            String sourceUrl,
            String sourceTitle,
            String sourceHost,
            String sourceSnippet,
            LocalDate publishedAt) {
    }
}
