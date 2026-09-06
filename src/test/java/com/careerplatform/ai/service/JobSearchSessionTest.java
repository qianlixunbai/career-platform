package com.careerplatform.ai.service;

import com.careerplatform.ai.client.JobSearchGateway.ProviderSearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchSessionTest {

    @Test
    void preservesExactProviderUrlButRebuildsHostFromParsedUrl() {
        String exactUrl = "https://Jobs.Example.com/openings/java?id=1#provider-fragment";
        JobSearchSession session = new JobSearchSession();

        List<JobSearchSession.SearchHit> hits = session.register(List.of(new ProviderSearchResult(
                exactUrl,
                "Java role",
                "attacker.invalid",
                "Provider snippet",
                LocalDate.of(2026, 9, 1))));

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().resultKey()).startsWith("result-");
        ProviderSearchResult resolved = session.resolve(hits.getFirst().resultKey());
        assertThat(resolved).isNotNull();
        assertThat(resolved.sourceUrl()).isEqualTo(exactUrl);
        assertThat(resolved.sourceHost()).isEqualTo("Jobs.Example.com");
        assertThat(resolved.sourceTitle()).isEqualTo("Java role");
        assertThat(resolved.sourceSnippet()).isEqualTo("Provider snippet");
        assertThat(resolved.publishedAt()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void deduplicatesCanonicalUrlsWhileKeepingFirstOriginalUrl() {
        String firstUrl = "https://JOBS.example.com/a/../b#first";
        String duplicateUrl = "https://jobs.example.com/b#second";
        JobSearchSession session = new JobSearchSession();

        List<JobSearchSession.SearchHit> hits = session.register(List.of(
                new ProviderSearchResult(firstUrl, "First", null, "first", null),
                new ProviderSearchResult(duplicateUrl, "Second", null, "second", null)));

        assertThat(hits).hasSize(1);
        assertThat(session.resolve(hits.getFirst().resultKey()).sourceUrl()).isEqualTo(firstUrl);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///tmp/job",
            "javascript:alert(1)",
            "data:text/plain,job",
            "http://localhost/path",
            "http://localhost./path",
            "http://api.localhost./path",
            "http://jobs.local./path",
            "http://127.0.0.1/path",
            "http://10.0.0.1/path",
            "http://192.168.1.1/path",
            "http://169.254.1.1/path",
            "http://[::1]/path",
            "http://user:pass@example.com/path",
            "http://2130706433/path",
            "http://0x7f000001/path",
            "http://017700000001/path",
            "http://example.com:65536/path"
    })
    void rejectsUnsafeOrBrowserLocalUrlVariants(String unsafeUrl) {
        JobSearchSession session = new JobSearchSession();

        assertThat(session.register(List.of(new ProviderSearchResult(
                unsafeUrl, "title", "provider-host", "snippet", null)))).isEmpty();
    }

    @Test
    void boundsDisplayedProviderText() {
        JobSearchSession session = new JobSearchSession();
        List<JobSearchSession.SearchHit> hits = session.register(List.of(new ProviderSearchResult(
                "https://jobs.example.com/openings/java",
                "t".repeat(600),
                "ignored.example",
                "s".repeat(2_200),
                null)));

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().sourceTitle()).hasSize(500);
        assertThat(hits.getFirst().sourceSnippet()).hasSize(2_000);
        ProviderSearchResult resolved = session.resolve(hits.getFirst().resultKey());
        assertThat(resolved.sourceTitle()).hasSize(500);
        assertThat(resolved.sourceSnippet()).hasSize(2_000);
        assertThat(resolved.sourceUrl()).isEqualTo("https://jobs.example.com/openings/java");
    }

    @Test
    void rejectsOverlongSourceUrlAndHost() {
        JobSearchSession session = new JobSearchSession();
        String overlongUrl = "https://jobs.example.com/" + "x".repeat(500);
        String overlongHost = "https://" + "a".repeat(151) + ".example/opening";

        assertThat(overlongUrl.length()).isGreaterThan(500);
        assertThat(session.register(List.of(new ProviderSearchResult(
                overlongUrl, "title", null, "snippet", null)))).isEmpty();
        assertThat(session.register(List.of(new ProviderSearchResult(
                overlongHost, "title", null, "snippet", null)))).isEmpty();
    }

    @Test
    void concurrentRegistrationsRemainConsistentAndSessionKeysDoNotCrossResolve() throws Exception {
        JobSearchSession firstSession = new JobSearchSession();
        JobSearchSession secondSession = new JobSearchSession();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<JobSearchSession.SearchHit>> first = executor.submit(() -> firstSession.register(List.of(
                    new ProviderSearchResult("https://jobs.example.com/first", "First", null, "first", null))));
            Future<List<JobSearchSession.SearchHit>> second = executor.submit(() -> secondSession.register(List.of(
                    new ProviderSearchResult("https://jobs.example.com/second", "Second", null, "second", null))));

            String firstKey = first.get().getFirst().resultKey();
            String secondKey = second.get().getFirst().resultKey();
            assertThat(firstKey).isNotEqualTo(secondKey);
            assertThat(firstSession.resolve(firstKey)).isNotNull();
            assertThat(secondSession.resolve(secondKey)).isNotNull();
            assertThat(firstSession.resolve(secondKey)).isNull();
            assertThat(secondSession.resolve(firstKey)).isNull();
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void ignoresMissingProviderFactsAndUnknownKeys() {
        JobSearchSession session = new JobSearchSession();

        assertThat(session.register(null)).isEmpty();
        assertThat(session.register(java.util.Collections.singletonList(null))).isEmpty();
        assertThat(session.resolve(null)).isNull();
        assertThat(session.resolve(" ")).isNull();
        assertThat(session.resolve("result-does-not-exist")).isNull();
    }
}
