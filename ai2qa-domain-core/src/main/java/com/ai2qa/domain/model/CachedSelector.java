package com.ai2qa.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model for a cached selector.
 *
 * @param id                 Unique identifier
 * @param tenantId           Tenant that owns this cache entry
 * @param goalHash           SHA-256 hash of the goal text
 * @param urlPattern         URL pattern this selector applies to
 * @param selector           The CSS/XPath selector (decrypted)
 * @param elementDescription Human-readable element description
 * @param successCount       Number of successful uses
 * @param failureCount       Number of failed attempts
 * @param lastUsedAt         When the selector was last used
 * @param createdAt          When the cache entry was created
 */
public record CachedSelector(
        UUID id,
        String tenantId,
        String goalHash,
        String urlPattern,
        String selector,
        String elementDescription,
        int successCount,
        int failureCount,
        Instant lastUsedAt,
        Instant createdAt) {

    /**
     * Creates a new cache entry.
     */
    /**
     * Creates a new cache entry.
     */
    public static CachedSelector create(
            UUID id,
            String tenantId,
            String goalHash,
            String urlPattern,
            String selector,
            String elementDescription,
            Instant now) {
        return new CachedSelector(
                id,
                tenantId,
                goalHash,
                urlPattern,
                selector,
                elementDescription,
                1,
                0,
                now,
                now);
    }

    /**
     * Returns a copy with incremented success count.
     */
    public CachedSelector withSuccessIncrement(Instant now) {
        return new CachedSelector(
                id, tenantId, goalHash, urlPattern, selector, elementDescription,
                successCount + 1, failureCount, now, createdAt);
    }

    /**
     * Returns a copy with incremented failure count.
     */
    public CachedSelector withFailureIncrement(Instant now) {
        return new CachedSelector(
                id, tenantId, goalHash, urlPattern, selector, elementDescription,
                successCount, failureCount + 1, now, createdAt);
    }

    /**
     * Calculates the success rate as a percentage.
     */
    public int successRatePercent() {
        int total = successCount + failureCount;
        if (total == 0)
            return 0;
        return (int) ((successCount * 100.0) / total);
    }

    /**
     * Checks if this selector is considered reliable.
     * A selector is reliable if it has a high success rate.
     */
    public boolean isReliable() {
        return successCount >= 3 && successRatePercent() >= 80;
    }

    /**
     * Checks if this selector should be invalidated due to too many failures.
     */
    public boolean shouldInvalidate() {
        return failureCount >= 3 && successRatePercent() < 50;
    }
}
