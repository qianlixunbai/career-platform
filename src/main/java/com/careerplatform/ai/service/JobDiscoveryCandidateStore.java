package com.careerplatform.ai.service;

import com.careerplatform.ai.dto.job.JobCandidate;
import com.careerplatform.common.exception.InvalidResourceStateException;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Bounded server-side storage for short-lived discovery candidates.
 *
 * <p>The store owns opaque IDs and user binding. Confirmation executes a
 * caller-supplied operation while holding the store lock; only a successful
 * operation consumes the candidate. This makes double-click and concurrent
 * confirmation attempts one-shot while keeping the operation free to use a
 * separate REQUIRES_NEW transaction.</p>
 */
@Component
public class JobDiscoveryCandidateStore {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    public static final int DEFAULT_MAX_PER_USER = 50;
    public static final int DEFAULT_MAX_GLOBAL = 1_000;
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final Clock clock;
    private final Duration ttl;
    private final int maxPerUser;
    private final int maxGlobal;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public JobDiscoveryCandidateStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_PER_USER, DEFAULT_MAX_GLOBAL);
    }

    public JobDiscoveryCandidateStore(Clock clock, Duration ttl) {
        this(clock, ttl, DEFAULT_MAX_PER_USER, DEFAULT_MAX_GLOBAL);
    }

    public JobDiscoveryCandidateStore(Clock clock, Duration ttl, int maxPerUser, int maxGlobal) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.ttl = Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxPerUser <= 0 || maxGlobal <= 0) {
            throw new IllegalArgumentException("candidate limits must be positive");
        }
        this.maxPerUser = maxPerUser;
        this.maxGlobal = maxGlobal;
    }

    /**
     * Atomically insert a batch. Capacity is checked before any ID is
     * generated or entry is added, so a rejected batch cannot partially land.
     */
    public List<JobCandidate> saveAll(Long userId, List<JobCandidate> candidates) {
        requireUserId(userId);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        for (JobCandidate candidate : candidates) {
            if (candidate == null) {
                throw new InvalidRequestException("候选岗位不能为空");
            }
        }

        synchronized (entries) {
            Instant now = clock.instant();
            cleanupExpired(now, null);
            int existingGlobal = entries.size();
            int existingUser = residentCountForUser(userId);
            if (candidates.size() > maxGlobal - existingGlobal
                    || candidates.size() > maxPerUser - existingUser) {
                throw new InvalidResourceStateException("候选暂存空间已满，请稍后重试");
            }

            Instant expiresAt = now.plus(ttl);
            List<JobCandidate> saved = new ArrayList<>(candidates.size());
            for (JobCandidate candidate : candidates) {
                String candidateId = newCandidateId();
                JobCandidate stored = new JobCandidate(candidateId, expiresAt,
                        candidate.sourceFacts(), candidate.extractedFields(), candidate.aiAdvice());
                entries.put(candidateId, new Entry(userId, stored));
                saved.add(stored);
            }
            return List.copyOf(saved);
        }
    }

    /** Resolve an unconsumed candidate after owner, TTL, and state checks. */
    public JobCandidate get(Long userId, String candidateId) {
        requireUserId(userId);
        requireCandidateId(candidateId);
        synchronized (entries) {
            cleanupExpired(clock.instant(), candidateId);
            Entry entry = lookup(userId, candidateId);
            return entry.candidate;
        }
    }

    /**
     * Run a confirmation operation under the one-shot lock. The candidate is
     * marked consumed only after the operation returns successfully. A thrown
     * validation, ownership, transaction, or commit failure leaves it usable.
     */
    public <T> T confirm(Long userId, String candidateId, Function<JobCandidate, T> operation) {
        requireUserId(userId);
        requireCandidateId(candidateId);
        Objects.requireNonNull(operation, "operation must not be null");
        synchronized (entries) {
            cleanupExpired(clock.instant(), candidateId);
            Entry entry = lookup(userId, candidateId);
            T result = operation.apply(entry.candidate);
            entry.consumed = true;
            return result;
        }
    }

    /** Number of stored entries, including unexpired consumed tombstones. */
    public int size() {
        synchronized (entries) {
            cleanupExpired(clock.instant(), null);
            return entries.size();
        }
    }

    private Entry lookup(Long userId, String candidateId) {
        Entry entry = entries.get(candidateId);
        if (entry == null || !Objects.equals(entry.userId, userId)) {
            // Do not reveal whether another user owns the opaque ID.
            throw new ResourceNotFoundException("资源不存在");
        }
        Instant now = clock.instant();
        if (!now.isBefore(entry.candidate.expiresAt())) {
            entries.remove(candidateId);
            throw new InvalidResourceStateException("候选岗位已过期");
        }
        if (entry.consumed) {
            throw new InvalidResourceStateException("候选岗位已确认");
        }
        return entry;
    }

    private void cleanupExpired(Instant now, String protectedCandidateId) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        int scanned = 0;
        while (iterator.hasNext() && scanned < Math.max(CLEANUP_BATCH_SIZE, maxGlobal)) {
            Map.Entry<String, Entry> mapEntry = iterator.next();
            scanned++;
            if (Objects.equals(mapEntry.getKey(), protectedCandidateId)) {
                continue;
            }
            if (!now.isBefore(mapEntry.getValue().candidate.expiresAt())) {
                iterator.remove();
            }
        }
    }

    private int residentCountForUser(Long userId) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (Objects.equals(entry.userId, userId)) {
                count++;
            }
        }
        return count;
    }

    private String newCandidateId() {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (entries.containsKey(id));
        return id;
    }

    private static void requireUserId(Long userId) {
        if (userId == null) {
            throw new InvalidRequestException("用户不能为空");
        }
    }

    private static void requireCandidateId(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw new InvalidRequestException("候选岗位不能为空");
        }
    }

    private static final class Entry {
        private final Long userId;
        private final JobCandidate candidate;
        private boolean consumed;

        private Entry(Long userId, JobCandidate candidate) {
            this.userId = userId;
            this.candidate = candidate;
        }
    }
}
