package com.ai2qa.application.security;

import com.ai2qa.application.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory rate limiting service to prevent abuse.
 *
 * Implements three rate limit tiers:
 * 1. Per-user: Limits requests per user per minute
 * 2. Per-IP: Limits requests per IP address per hour
 * 3. Per-target: Limits requests to the same target domain per hour
 *
 * Uses ConcurrentHashMap for thread-safe operation with scheduled cleanup
 * to prevent memory leaks from accumulated keys.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    // Rate limit buckets
    private final ConcurrentHashMap<String, RateLimitBucket> userBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitBucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitBucket> targetBuckets = new ConcurrentHashMap<>();

    // Configuration
    private final boolean enabled;
    private final int userLimitPerMinute;
    private final int ipLimitPerHour;
    private final int targetLimitPerHour;

    // Window durations in milliseconds
    private static final long USER_WINDOW_MS = 60_000; // 1 minute
    private static final long IP_WINDOW_MS = 3_600_000; // 1 hour
    private static final long TARGET_WINDOW_MS = 3_600_000; // 1 hour
    private static final long CLEANUP_INTERVAL_MS = 300_000; // 5 minutes

    public RateLimitService(
            @Value("${ai2qa.rate-limit.enabled:true}") boolean enabled,
            @Value("${ai2qa.rate-limit.user-per-minute:10}") int userLimitPerMinute,
            @Value("${ai2qa.rate-limit.ip-per-hour:30}") int ipLimitPerHour,
            @Value("${ai2qa.rate-limit.target-per-hour:100}") int targetLimitPerHour) {

        this.enabled = enabled;
        this.userLimitPerMinute = userLimitPerMinute;
        this.ipLimitPerHour = ipLimitPerHour;
        this.targetLimitPerHour = targetLimitPerHour;

        log.info("RateLimitService initialized: enabled={}, userLimit={}/min, ipLimit={}/hour, targetLimit={}/hour",
                enabled, userLimitPerMinute, ipLimitPerHour, targetLimitPerHour);
    }

    /**
     * Checks all rate limits for a request.
     * Throws RateLimitExceededException if any limit is exceeded.
     *
     * @param userId       The user ID (can be null for anonymous users)
     * @param clientIp     The client IP address
     * @param targetDomain The target domain being tested
     * @throws RateLimitExceededException if any rate limit is exceeded
     */
    public void checkRateLimits(String userId, String clientIp, String targetDomain) {
        if (!enabled) {
            return;
        }

        // Check user rate limit
        if (userId != null && !userId.isBlank()) {
            checkUserLimit(userId);
        }

        // Check IP rate limit
        if (clientIp != null && !clientIp.isBlank()) {
            checkIpLimit(clientIp);
        }

        // Check target domain rate limit
        if (targetDomain != null && !targetDomain.isBlank()) {
            checkTargetLimit(targetDomain);
        }
    }

    /**
     * Checks and increments the user rate limit.
     */
    private void checkUserLimit(String userId) {
        String key = "user:" + userId;
        RateLimitBucket bucket = userBuckets.computeIfAbsent(key,
                k -> new RateLimitBucket(USER_WINDOW_MS));

        if (!bucket.tryAcquire(userLimitPerMinute)) {
            log.warn("User rate limit exceeded: userId={}", userId);
            throw new RateLimitExceededException(
                    "User rate limit exceeded. Maximum " + userLimitPerMinute + " tests per minute.");
        }
    }

    /**
     * Checks and increments the IP rate limit.
     */
    private void checkIpLimit(String clientIp) {
        String key = "ip:" + clientIp;
        RateLimitBucket bucket = ipBuckets.computeIfAbsent(key,
                k -> new RateLimitBucket(IP_WINDOW_MS));

        if (!bucket.tryAcquire(ipLimitPerHour)) {
            log.warn("IP rate limit exceeded: clientIp={}", clientIp);
            throw new RateLimitExceededException(
                    "IP rate limit exceeded. Maximum " + ipLimitPerHour + " tests per hour.");
        }
    }

    /**
     * Checks and increments the target domain rate limit.
     */
    private void checkTargetLimit(String targetDomain) {
        String key = "target:" + targetDomain.toLowerCase();
        RateLimitBucket bucket = targetBuckets.computeIfAbsent(key,
                k -> new RateLimitBucket(TARGET_WINDOW_MS));

        if (!bucket.tryAcquire(targetLimitPerHour)) {
            log.warn("Target rate limit exceeded: domain={}", targetDomain);
            throw new RateLimitExceededException(
                    "Target rate limit exceeded. Maximum " + targetLimitPerHour + " tests per hour to " + targetDomain);
        }
    }

    /**
     * Scheduled cleanup of expired buckets to prevent memory leaks.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpiredBuckets() {
        long now = Instant.now().toEpochMilli();

        int userCleaned = cleanupMap(userBuckets, now);
        int ipCleaned = cleanupMap(ipBuckets, now);
        int targetCleaned = cleanupMap(targetBuckets, now);

        if (userCleaned + ipCleaned + targetCleaned > 0) {
            log.debug("Rate limit cleanup: removed {} user, {} IP, {} target buckets",
                    userCleaned, ipCleaned, targetCleaned);
        }
    }

    private int cleanupMap(ConcurrentHashMap<String, RateLimitBucket> map, long now) {
        AtomicInteger count = new AtomicInteger(0);
        map.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                count.incrementAndGet();
            }
            return expired;
        });
        return count.get();
    }

    /**
     * Gets current rate limit statistics for monitoring.
     */
    public RateLimitStats getStats() {
        return new RateLimitStats(
                userBuckets.size(),
                ipBuckets.size(),
                targetBuckets.size()
        );
    }

    /**
     * Rate limit bucket using sliding window algorithm.
     */
    private static class RateLimitBucket {
        private final long windowMs;
        private long windowStart;
        private int count;

        RateLimitBucket(long windowMs) {
            this.windowMs = windowMs;
            this.windowStart = Instant.now().toEpochMilli();
            this.count = 0;
        }

        synchronized boolean tryAcquire(int limit) {
            long now = Instant.now().toEpochMilli();

            // Check if window has expired and reset if so
            if (now - windowStart > windowMs) {
                windowStart = now;
                count = 0;
            }

            // Check if we're within limits
            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }

        synchronized boolean isExpired(long now) {
            // Consider expired if no activity for 2x the window duration
            return now - windowStart > windowMs * 2;
        }
    }

    /**
     * Statistics for monitoring rate limit usage.
     */
    public record RateLimitStats(
            int activeUserBuckets,
            int activeIpBuckets,
            int activeTargetBuckets
    ) {}
}
