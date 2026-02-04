package com.ai2qa.domain.port;

import com.ai2qa.domain.model.CachedSelector;

import java.time.Instant;
import java.util.Optional;

/**
 * Port for selector cache operations.
 *
 * <p>
 * Provides caching of successful selectors to reduce AI API calls.
 */
public interface SelectorCachePort {

    /**
     * Finds a cached selector for the given parameters.
     *
     * @param tenantId   The tenant identifier
     * @param goalHash   SHA-256 hash of the goal text
     * @param urlPattern URL pattern to match
     * @return Cached selector if found
     */
    Optional<CachedSelector> find(String tenantId, String goalHash, String urlPattern);

    /**
     * Saves or updates a cached selector.
     *
     * @param cachedSelector The selector to cache
     */
    void save(CachedSelector cachedSelector);

    /**
     * Records a successful use of a cached selector.
     *
     * @param tenantId   The tenant identifier
     * @param goalHash   SHA-256 hash of the goal text
     * @param urlPattern URL pattern
     * @param now        Current timestamp
     */
    void recordSuccess(String tenantId, String goalHash, String urlPattern, Instant now);

    /**
     * Records a failed use of a cached selector.
     *
     * @param tenantId   The tenant identifier
     * @param goalHash   SHA-256 hash of the goal text
     * @param urlPattern URL pattern
     * @param now        Current timestamp
     */
    void recordFailure(String tenantId, String goalHash, String urlPattern, Instant now);

    /**
     * Invalidates (deletes) a cached selector.
     *
     * @param tenantId   The tenant identifier
     * @param goalHash   SHA-256 hash of the goal text
     * @param urlPattern URL pattern
     */
    void invalidate(String tenantId, String goalHash, String urlPattern);

    /**
     * Cleans up stale cache entries older than the specified days.
     *
     * @param olderThanDays Number of days since last use
     * @return Number of entries deleted
     */
    int cleanupStale(int olderThanDays);
}
