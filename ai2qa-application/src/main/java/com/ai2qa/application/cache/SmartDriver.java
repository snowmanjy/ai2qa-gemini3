package com.ai2qa.application.cache;

import com.ai2qa.domain.model.CachedSelector;
import com.ai2qa.domain.model.DomSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Smart element selection with caching.
 *
 * <p>Implements "Trust, but Verify" pattern:
 * <ol>
 *   <li>Check cache for existing selector (fast path: $0 AI cost)</li>
 *   <li>Try cached selector, verify it works</li>
 *   <li>If cached selector fails, fall back to AI (slow path)</li>
 *   <li>Cache new selectors for future use</li>
 * </ol>
 *
 * <p>This significantly reduces AI API costs for repeated interactions
 * with the same elements across test runs.
 */
@Component
public class SmartDriver {

    private static final Logger log = LoggerFactory.getLogger(SmartDriver.class);

    private final SelectorCacheService cacheService;
    private final SelectorFinder aiSelectorFinder;

    public SmartDriver(
            SelectorCacheService cacheService,
            SelectorFinder aiSelectorFinder
    ) {
        this.cacheService = cacheService;
        this.aiSelectorFinder = aiSelectorFinder;
    }

    /**
     * Interface for AI-based selector finding.
     */
    public interface SelectorFinder {
        /**
         * Finds a selector for the described element using AI.
         *
         * @param elementDescription Description of the element to find
         * @param snapshot           Current DOM snapshot
         * @return Selector if found
         */
        Optional<String> findSelector(String elementDescription, DomSnapshot snapshot);
    }

    /**
     * Result of a smart selector lookup.
     */
    public record SelectorResult(
            String selector,
            boolean fromCache,
            int cacheSuccessCount
    ) {
        public static SelectorResult cached(String selector, int successCount) {
            return new SelectorResult(selector, true, successCount);
        }

        public static SelectorResult fromAi(String selector) {
            return new SelectorResult(selector, false, 0);
        }
    }

    /**
     * Finds a selector for the described element, using cache when possible.
     *
     * <p>This is the main entry point for the "Trust, but Verify" pattern.
     *
     * @param tenantId           The tenant identifier
     * @param elementDescription Description of the element to find
     * @param url                Current page URL
     * @param snapshot           Current DOM snapshot
     * @param selectorVerifier   Function to verify if selector works (returns true if valid)
     * @return Selector result with cache metadata
     */
    public Optional<SelectorResult> findElement(
            String tenantId,
            String elementDescription,
            String url,
            DomSnapshot snapshot,
            Function<String, Boolean> selectorVerifier
    ) {
        log.debug("Finding element: '{}'", elementDescription);

        // Step 1: Check cache (fast path)
        Optional<CachedSelector> cached = cacheService.findCached(tenantId, elementDescription, url);

        if (cached.isPresent()) {
            CachedSelector cachedSelector = cached.get();
            String selector = cachedSelector.selector();

            log.debug("Cache hit! Trying cached selector: {} (success rate: {}%)",
                    selector, cachedSelector.successRatePercent());

            // Step 2: Verify cached selector still works
            boolean selectorWorks = verifySelectorSafely(selector, selectorVerifier);

            if (selectorWorks) {
                // Fast path success: $0 AI cost
                cacheService.recordSuccess(tenantId, elementDescription, url);
                log.info("Cached selector verified: {} (successes: {})",
                        selector, cachedSelector.successCount() + 1);
                return Optional.of(SelectorResult.cached(selector, cachedSelector.successCount() + 1));
            }

            // Cached selector failed - record failure
            cacheService.recordFailure(tenantId, elementDescription, url);
            log.warn("Cached selector failed verification: {}, falling back to AI", selector);
        }

        // Step 3: Slow path - ask AI
        log.info("Cache miss for '{}', querying AI...", truncate(elementDescription, 50));
        Optional<String> aiSelector = aiSelectorFinder.findSelector(elementDescription, snapshot);

        if (aiSelector.isEmpty()) {
            log.warn("AI could not find selector for: '{}'", elementDescription);
            return Optional.empty();
        }

        String newSelector = aiSelector.get();

        // Step 4: Verify AI selector works
        boolean aiSelectorWorks = verifySelectorSafely(newSelector, selectorVerifier);

        if (!aiSelectorWorks) {
            log.warn("AI selector failed verification: {}", newSelector);
            return Optional.empty();
        }

        // Step 5: Cache the successful selector
        cacheService.cacheSelector(tenantId, elementDescription, url, newSelector, elementDescription);
        log.info("Cached new AI selector: {} for '{}'", newSelector, truncate(elementDescription, 50));

        return Optional.of(SelectorResult.fromAi(newSelector));
    }

    /**
     * Simplified version without verification (trusts the selector).
     *
     * <p>Use this when you want to get the selector without immediately verifying it.
     * You should still call {@link #recordOutcome} after using the selector.
     *
     * @param tenantId           The tenant identifier
     * @param elementDescription Description of the element to find
     * @param url                Current page URL
     * @param snapshot           Current DOM snapshot
     * @return Selector result with cache metadata
     */
    public Optional<SelectorResult> findElementWithoutVerification(
            String tenantId,
            String elementDescription,
            String url,
            DomSnapshot snapshot
    ) {
        log.debug("Finding element (no verification): '{}'", elementDescription);

        // Check cache first
        Optional<CachedSelector> cached = cacheService.findCached(tenantId, elementDescription, url);

        if (cached.isPresent()) {
            CachedSelector cachedSelector = cached.get();
            log.debug("Cache hit: {} (success rate: {}%)",
                    cachedSelector.selector(), cachedSelector.successRatePercent());
            return Optional.of(SelectorResult.cached(
                    cachedSelector.selector(),
                    cachedSelector.successCount()
            ));
        }

        // Slow path - ask AI
        log.info("Cache miss, querying AI for '{}'", truncate(elementDescription, 50));
        Optional<String> aiSelector = aiSelectorFinder.findSelector(elementDescription, snapshot);

        if (aiSelector.isEmpty()) {
            return Optional.empty();
        }

        // Cache immediately (will be updated based on recordOutcome)
        String newSelector = aiSelector.get();
        cacheService.cacheSelector(tenantId, elementDescription, url, newSelector, elementDescription);

        return Optional.of(SelectorResult.fromAi(newSelector));
    }

    /**
     * Records the outcome of using a selector.
     *
     * <p>Call this after using a selector obtained via {@link #findElementWithoutVerification}.
     *
     * @param tenantId           The tenant identifier
     * @param elementDescription Description of the element
     * @param url                Current page URL
     * @param success            Whether the selector worked
     */
    public void recordOutcome(
            String tenantId,
            String elementDescription,
            String url,
            boolean success
    ) {
        if (success) {
            cacheService.recordSuccess(tenantId, elementDescription, url);
        } else {
            cacheService.recordFailure(tenantId, elementDescription, url);
        }
    }

    /**
     * Invalidates a cached selector.
     *
     * <p>Use this when you know a selector is no longer valid.
     *
     * @param tenantId           The tenant identifier
     * @param elementDescription Description of the element
     * @param url                Current page URL
     */
    public void invalidateSelector(
            String tenantId,
            String elementDescription,
            String url
    ) {
        cacheService.invalidate(tenantId, elementDescription, url);
    }

    /**
     * Safely verifies a selector, catching any exceptions.
     */
    private boolean verifySelectorSafely(String selector, Function<String, Boolean> verifier) {
        try {
            return verifier.apply(selector);
        } catch (Exception e) {
            log.debug("Selector verification threw exception: {}", e.getMessage());
            return false;
        }
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
