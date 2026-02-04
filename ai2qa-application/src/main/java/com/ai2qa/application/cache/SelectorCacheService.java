package com.ai2qa.application.cache;

import com.ai2qa.domain.model.CachedSelector;
import com.ai2qa.domain.port.SelectorCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for selector caching.
 *
 * <p>
 * Provides goal text hashing and URL pattern normalization
 * on top of the domain port.
 */
@Service
public class SelectorCacheService {

    private static final Logger log = LoggerFactory.getLogger(SelectorCacheService.class);

    private final SelectorCachePort cachePort;
    private final Clock clock;

    public SelectorCacheService(SelectorCachePort cachePort, Clock clock) {
        this.cachePort = cachePort;
        this.clock = clock;
    }

    /**
     * Finds a cached selector for the given goal text and URL.
     *
     * @param tenantId The tenant identifier
     * @param goalText The goal text (will be hashed)
     * @param url      The page URL (will be normalized to pattern)
     * @return Cached selector if found
     */
    public Optional<CachedSelector> findCached(String tenantId, String goalText, String url) {
        String goalHash = hashGoal(goalText);
        String urlPattern = normalizeUrl(url);

        Optional<CachedSelector> cached = cachePort.find(tenantId, goalHash, urlPattern);
        cached.ifPresent(c -> log.debug("Cache hit for goal hash: {}, selector success rate: {}%",
                goalHash, c.successRatePercent()));

        return cached;
    }

    /**
     * Caches a successful selector.
     *
     * @param tenantId           The tenant identifier
     * @param goalText           The goal text
     * @param url                The page URL
     * @param selector           The CSS/XPath selector
     * @param elementDescription Description of the element
     */
    public void cacheSelector(
            String tenantId,
            String goalText,
            String url,
            String selector,
            String elementDescription) {
        String goalHash = hashGoal(goalText);
        String urlPattern = normalizeUrl(url);

        CachedSelector cached = CachedSelector.create(
                UUID.randomUUID(),
                tenantId,
                goalHash,
                urlPattern,
                selector,
                elementDescription,
                clock.instant());

        cachePort.save(cached);
        log.info("Cached selector for goal: '{}' -> {}", truncate(goalText, 50), selector);
    }

    /**
     * Records successful use of a cached selector.
     *
     * @param tenantId The tenant identifier
     * @param goalText The goal text
     * @param url      The page URL
     */
    public void recordSuccess(String tenantId, String goalText, String url) {
        String goalHash = hashGoal(goalText);
        String urlPattern = normalizeUrl(url);
        cachePort.recordSuccess(tenantId, goalHash, urlPattern, clock.instant());
    }

    /**
     * Records failed use of a cached selector.
     *
     * @param tenantId The tenant identifier
     * @param goalText The goal text
     * @param url      The page URL
     */
    public void recordFailure(String tenantId, String goalText, String url) {
        String goalHash = hashGoal(goalText);
        String urlPattern = normalizeUrl(url);
        cachePort.recordFailure(tenantId, goalHash, urlPattern, clock.instant());
    }

    /**
     * Invalidates a cached selector.
     *
     * @param tenantId The tenant identifier
     * @param goalText The goal text
     * @param url      The page URL
     */
    public void invalidate(String tenantId, String goalText, String url) {
        String goalHash = hashGoal(goalText);
        String urlPattern = normalizeUrl(url);
        cachePort.invalidate(tenantId, goalHash, urlPattern);
        log.info("Invalidated cache for goal: '{}'", truncate(goalText, 50));
    }

    /**
     * Cleans up stale cache entries.
     *
     * @param olderThanDays Number of days since last use
     * @return Number of entries deleted
     */
    public int cleanupStale(int olderThanDays) {
        return cachePort.cleanupStale(olderThanDays);
    }

    /**
     * Hashes goal text using SHA-256.
     *
     * @param goalText The goal text to hash
     * @return Hexadecimal hash string
     */
    String hashGoal(String goalText) {
        if (goalText == null || goalText.isBlank()) {
            throw new IllegalArgumentException("Goal text cannot be null or blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(goalText.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Normalizes URL to a pattern for matching.
     *
     * <p>
     * Strips query parameters, fragments, and trailing slashes.
     * Replaces common dynamic segments like IDs with placeholders.
     */
    String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String normalized = url.trim();

        // Remove fragment
        int fragmentIndex = normalized.indexOf('#');
        if (fragmentIndex > 0) {
            normalized = normalized.substring(0, fragmentIndex);
        }

        // Remove query parameters
        int queryIndex = normalized.indexOf('?');
        if (queryIndex > 0) {
            normalized = normalized.substring(0, queryIndex);
        }

        // Remove trailing slash
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // Replace UUID-like segments with placeholder
        normalized = normalized.replaceAll(
                "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
                "/{id}");

        // Replace numeric ID segments with placeholder
        normalized = normalized.replaceAll("/\\d+(?=/|$)", "/{id}");

        return normalized;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLength
                ? text.substring(0, maxLength) + "..."
                : text;
    }
}
