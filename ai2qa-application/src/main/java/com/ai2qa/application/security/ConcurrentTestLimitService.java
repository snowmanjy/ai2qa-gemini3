package com.ai2qa.application.security;

import com.ai2qa.application.exception.ConcurrentLimitExceededException;
import com.ai2qa.application.exception.ConcurrentLimitExceededException.LimitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to limit concurrent test executions.
 *
 * <p>Implements two concurrent limit tiers:
 * <ul>
 *   <li>Per-user: Max concurrent tests per user (default: 3)</li>
 *   <li>Global: Max concurrent tests system-wide (default: 50)</li>
 * </ul>
 *
 * <p>This prevents API rate limit exhaustion and ensures fair resource usage.
 * Uses in-memory tracking with scheduled cleanup for orphaned entries.
 */
@Service
public class ConcurrentTestLimitService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentTestLimitService.class);

    // Tracking structures
    // Map of tenantId -> Set of active testRunIds
    private final ConcurrentHashMap<String, Set<ActiveTest>> userActiveTests = new ConcurrentHashMap<>();
    // Global set of all active tests
    private final Set<ActiveTest> globalActiveTests = ConcurrentHashMap.newKeySet();

    // Configuration
    private final boolean enabled;
    private final int maxConcurrentPerUser;
    private final int maxConcurrentGlobal;

    // Stale entry timeout (tests running longer than this are considered orphaned)
    private static final long STALE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    public ConcurrentTestLimitService(
            @Value("${ai2qa.concurrent-limit.enabled:true}") boolean enabled,
            @Value("${ai2qa.concurrent-limit.max-per-user:3}") int maxConcurrentPerUser,
            @Value("${ai2qa.concurrent-limit.max-global:50}") int maxConcurrentGlobal) {

        this.enabled = enabled;
        this.maxConcurrentPerUser = maxConcurrentPerUser;
        this.maxConcurrentGlobal = maxConcurrentGlobal;

        log.info("ConcurrentTestLimitService initialized: enabled={}, maxPerUser={}, maxGlobal={}",
                enabled, maxConcurrentPerUser, maxConcurrentGlobal);
    }

    /**
     * Attempts to acquire a slot for a new test execution.
     * Throws ConcurrentLimitExceededException if limits are exceeded.
     *
     * @param tenantId  The tenant/user ID
     * @param testRunId The test run ID (as string)
     * @throws ConcurrentLimitExceededException if user or global limits are exceeded
     */
    public void acquire(String tenantId, String testRunId) {
        if (!enabled) {
            return;
        }

        ActiveTest activeTest = new ActiveTest(tenantId, testRunId, Instant.now().toEpochMilli());

        // Check and acquire global slot first
        synchronized (globalActiveTests) {
            if (globalActiveTests.size() >= maxConcurrentGlobal) {
                log.warn("Global concurrent limit exceeded: current={}, max={}, tenant={}",
                        globalActiveTests.size(), maxConcurrentGlobal, tenantId);
                throw new ConcurrentLimitExceededException(
                        LimitType.GLOBAL, globalActiveTests.size(), maxConcurrentGlobal);
            }

            // Check user limit
            Set<ActiveTest> userTests = userActiveTests.computeIfAbsent(tenantId,
                    k -> ConcurrentHashMap.newKeySet());

            synchronized (userTests) {
                if (userTests.size() >= maxConcurrentPerUser) {
                    log.warn("User concurrent limit exceeded: tenant={}, current={}, max={}",
                            tenantId, userTests.size(), maxConcurrentPerUser);
                    throw new ConcurrentLimitExceededException(
                            LimitType.USER, userTests.size(), maxConcurrentPerUser);
                }

                // Acquire both slots atomically
                userTests.add(activeTest);
                globalActiveTests.add(activeTest);
            }
        }

        log.debug("Acquired concurrent slot: tenant={}, testRun={}, userCount={}, globalCount={}",
                tenantId, testRunId, getUserActiveCount(tenantId), globalActiveTests.size());
    }

    /**
     * Releases a slot when test execution completes.
     * Should be called in a finally block to ensure cleanup.
     *
     * @param tenantId  The tenant/user ID
     * @param testRunId The test run ID
     */
    public void release(String tenantId, String testRunId) {
        if (!enabled) {
            return;
        }

        ActiveTest toRemove = null;

        // Synchronize on globalActiveTests to prevent race with acquire() and iteration
        synchronized (globalActiveTests) {
            // Find and remove from global set
            for (ActiveTest test : globalActiveTests) {
                if (test.tenantId().equals(tenantId) && test.testRunId().equals(testRunId)) {
                    toRemove = test;
                    break;
                }
            }

            if (toRemove != null) {
                globalActiveTests.remove(toRemove);

                // Remove from user set
                Set<ActiveTest> userTests = userActiveTests.get(tenantId);
                if (userTests != null) {
                    synchronized (userTests) {
                        userTests.remove(toRemove);
                        // Clean up empty user sets
                        if (userTests.isEmpty()) {
                            userActiveTests.remove(tenantId);
                        }
                    }
                }

                log.debug("Released concurrent slot: tenant={}, testRun={}, userCount={}, globalCount={}",
                        tenantId, testRunId, getUserActiveCount(tenantId), globalActiveTests.size());
            } else {
                log.debug("No slot to release (already released or never acquired): tenant={}, testRun={}",
                        tenantId, testRunId);
            }
        }
    }

    /**
     * Checks if a user can start a new test without actually acquiring.
     * Useful for UI feedback.
     *
     * @param tenantId The tenant/user ID
     * @return Optional containing error message if limit would be exceeded, empty if OK
     */
    public Optional<String> checkAvailability(String tenantId) {
        if (!enabled) {
            return Optional.empty();
        }

        if (globalActiveTests.size() >= maxConcurrentGlobal) {
            return Optional.of(String.format(
                    "System is at capacity (%d/%d concurrent tests). Please try again shortly.",
                    globalActiveTests.size(), maxConcurrentGlobal));
        }

        int userCount = getUserActiveCount(tenantId);
        if (userCount >= maxConcurrentPerUser) {
            return Optional.of(String.format(
                    "You have %d/%d concurrent tests running. Please wait for a test to complete.",
                    userCount, maxConcurrentPerUser));
        }

        return Optional.empty();
    }

    /**
     * Gets current concurrent test count for a user.
     */
    public int getUserActiveCount(String tenantId) {
        Set<ActiveTest> userTests = userActiveTests.get(tenantId);
        return userTests != null ? userTests.size() : 0;
    }

    /**
     * Gets current global concurrent test count.
     */
    public int getGlobalActiveCount() {
        return globalActiveTests.size();
    }

    /**
     * Gets statistics for monitoring.
     */
    public ConcurrentLimitStats getStats() {
        return new ConcurrentLimitStats(
                globalActiveTests.size(),
                maxConcurrentGlobal,
                userActiveTests.size(),
                maxConcurrentPerUser
        );
    }

    /**
     * Scheduled cleanup of stale entries.
     * Handles cases where tests crash without releasing their slots.
     *
     * <p>Uses Set.copyOf() to create a snapshot before iteration to avoid
     * ConcurrentModificationException when release() modifies the set.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupStaleEntries() {
        long now = Instant.now().toEpochMilli();
        int cleaned = 0;

        // Create a snapshot to avoid ConcurrentModificationException
        // since release() modifies globalActiveTests during iteration
        Set<ActiveTest> snapshot = Set.copyOf(globalActiveTests);

        for (ActiveTest test : snapshot) {
            if (now - test.startedAt() > STALE_TIMEOUT_MS) {
                log.warn("Removing stale test entry: tenant={}, testRun={}, age={}ms",
                        test.tenantId(), test.testRunId(), now - test.startedAt());
                release(test.tenantId(), test.testRunId());
                cleaned++;
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned {} stale concurrent test entries", cleaned);
        }
    }

    /**
     * Force releases all slots for a tenant.
     * Used for emergency cleanup or tenant deactivation.
     */
    public void forceReleaseAll(String tenantId) {
        Set<ActiveTest> userTests = userActiveTests.get(tenantId);
        if (userTests != null) {
            for (ActiveTest test : Set.copyOf(userTests)) {
                release(test.tenantId(), test.testRunId());
            }
            log.info("Force released all slots for tenant: {}", tenantId);
        }
    }

    /**
     * Record tracking an active test.
     */
    private record ActiveTest(String tenantId, String testRunId, long startedAt) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ActiveTest that)) return false;
            return tenantId.equals(that.tenantId) && testRunId.equals(that.testRunId);
        }

        @Override
        public int hashCode() {
            return 31 * tenantId.hashCode() + testRunId.hashCode();
        }
    }

    /**
     * Statistics for monitoring concurrent test limits.
     */
    public record ConcurrentLimitStats(
            int currentGlobalCount,
            int maxGlobal,
            int activeUserCount,
            int maxPerUser
    ) {
        public double globalUtilization() {
            return maxGlobal > 0 ? (double) currentGlobalCount / maxGlobal : 0.0;
        }
    }
}
