package com.careerplatform.ai.service;

import com.careerplatform.ai.dto.job.JobCandidate;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobDiscoveryCandidateStoreTest {
    private static final Long USER_ID = 41L;
    private static final Long OTHER_USER_ID = 42L;

    @Test
    void assignsOpaqueIdsAndBindsOwnerUntilExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-05T00:00:00Z"));
        JobDiscoveryCandidateStore store = new JobDiscoveryCandidateStore(
                clock, Duration.ofMinutes(15), 50, 1_000);

        JobCandidate saved = store.saveAll(USER_ID, List.of(candidate("source-a"))).getFirst();

        assertThat(saved.candidateId()).isNotBlank().doesNotContain("source-a");
        assertThat(saved.expiresAt()).isEqualTo(Instant.parse("2026-09-05T00:15:00Z"));
        assertThat(store.get(USER_ID, saved.candidateId())).isEqualTo(saved);
        assertThatThrownBy(() -> store.get(OTHER_USER_ID, saved.candidateId()))
                .isInstanceOf(ResourceNotFoundException.class);

        clock.advance(Duration.ofMinutes(15));
        assertThatThrownBy(() -> store.get(USER_ID, saved.candidateId()))
                .isInstanceOf(InvalidResourceStateException.class)
                .hasMessage("候选岗位已过期");
        assertThat(store.size()).isZero();
    }

    @Test
    void failureDoesNotConsumeAndSuccessConsumesExactlyOnce() {
        JobDiscoveryCandidateStore store = new JobDiscoveryCandidateStore(
                Clock.systemUTC(), Duration.ofMinutes(15), 50, 1_000);
        JobCandidate saved = store.saveAll(USER_ID, List.of(candidate("source-b"))).getFirst();

        assertThatThrownBy(() -> store.confirm(USER_ID, saved.candidateId(), ignored -> {
            throw new IllegalStateException("simulated committed-operation failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(store.get(USER_ID, saved.candidateId())).isEqualTo(saved);

        Long confirmedId = store.confirm(USER_ID, saved.candidateId(), ignored -> 123L);
        assertThat(confirmedId).isEqualTo(123L);
        assertThatThrownBy(() -> store.confirm(USER_ID, saved.candidateId(), ignored -> 456L))
                .isInstanceOf(InvalidResourceStateException.class)
                .hasMessage("候选岗位已确认");
        assertThatThrownBy(() -> store.get(OTHER_USER_ID, saved.candidateId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void batchCapacityIsAtomicAndRejectsInsteadOfEvictingExistingCandidates() {
        JobDiscoveryCandidateStore store = new JobDiscoveryCandidateStore(
                Clock.systemUTC(), Duration.ofMinutes(15), 2, 2);
        List<JobCandidate> firstBatch = store.saveAll(USER_ID,
                List.of(candidate("source-c"), candidate("source-d")));

        assertThat(firstBatch).hasSize(2);
        assertThatThrownBy(() -> store.saveAll(USER_ID, List.of(candidate("source-e"))))
                .isInstanceOf(InvalidResourceStateException.class);
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.get(USER_ID, firstBatch.getFirst().candidateId())).isEqualTo(firstBatch.getFirst());
    }

    @Test
    void consumedTombstonesAreResidentOnlyUntilTtlAndCannotGrowWithoutBound() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-05T00:00:00Z"));
        JobDiscoveryCandidateStore store = new JobDiscoveryCandidateStore(
                clock, Duration.ofMinutes(15), 2, 2);
        JobCandidate first = store.saveAll(USER_ID, List.of(candidate("source-f"))).getFirst();
        store.confirm(USER_ID, first.candidateId(), ignored -> true);
        JobCandidate second = store.saveAll(USER_ID, List.of(candidate("source-g"))).getFirst();
        store.confirm(USER_ID, second.candidateId(), ignored -> true);

        assertThat(store.size()).isEqualTo(2);
        for (int attempt = 0; attempt < 40; attempt++) {
            assertThatThrownBy(() -> store.saveAll(USER_ID, List.of(candidate("source-h"))))
                    .isInstanceOf(InvalidResourceStateException.class);
            assertThat(store.size()).isEqualTo(2);
        }

        clock.advance(Duration.ofMinutes(15));
        JobCandidate afterTtl = store.saveAll(USER_ID, List.of(candidate("source-i"))).getFirst();
        assertThat(afterTtl.candidateId()).isNotBlank();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void concurrentConfirmationsAllowAtMostOneSuccessfulOperation() throws Exception {
        JobDiscoveryCandidateStore store = new JobDiscoveryCandidateStore(
                Clock.systemUTC(), Duration.ofMinutes(15), 50, 1_000);
        JobCandidate saved = store.saveAll(USER_ID, List.of(candidate("source-j"))).getFirst();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> confirm = () -> {
                try {
                    store.confirm(USER_ID, saved.candidateId(), ignored -> {
                        try {
                            Thread.sleep(30L);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                        return true;
                    });
                    return true;
                } catch (InvalidResourceStateException exception) {
                    return false;
                }
            };
            List<Future<Boolean>> futures = executor.invokeAll(List.of(confirm, confirm));
            assertThat(futures.stream().map(this::getUnchecked).toList())
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
    }

    private JobCandidate candidate(String urlSuffix) {
        return new JobCandidate(
                null,
                null,
                new JobCandidate.SourceFacts(
                        "https://jobs.example.com/" + urlSuffix,
                        "Source title",
                        "jobs.example.com",
                        "Search snippet",
                        null,
                        "TAVILY"),
                new JobCandidate.ExtractedFields("Source title", null, "Shanghai", null),
                new JobCandidate.AiAdvice(1, "Fit", List.of(), List.of(), List.of(), List.of()));
    }

    private Boolean getUnchecked(Future<Boolean> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        } catch (ExecutionException exception) {
            throw new AssertionError(exception.getCause());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
