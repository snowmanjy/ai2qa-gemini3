package com.ai2qa.application.knowledge;

import com.ai2qa.domain.model.knowledge.FlowStrategy;
import com.ai2qa.domain.model.knowledge.FlowStrategyFactory;
import com.ai2qa.domain.model.knowledge.FlowStep;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorAlternative;
import com.ai2qa.domain.model.knowledge.SelectorType;
import com.ai2qa.domain.model.knowledge.SitePattern;
import com.ai2qa.domain.model.knowledge.SitePatternFactory;
import com.ai2qa.domain.model.knowledge.SitePatternId;
import com.ai2qa.domain.model.knowledge.Visibility;
import com.ai2qa.domain.repository.KnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for managing QA knowledge patterns.
 *
 * <p>Handles storing patterns discovered during test runs, updating success/failure
 * statistics, and retrieving patterns for future test runs.
 */
@Service
@Transactional
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    // Keys that are too generic or handled by the system (blocked from storage)
    private static final java.util.Set<String> BLOCKED_KEYS = java.util.Set.of(
            // Consent-related patterns - handled automatically by ObstacleDetector
            "consent_button", "consent_selector", "consent",
            "cookie_button", "cookie_selector", "cookie",
            "accept_button", "accept_selector", "accept",
            "agree_button", "agree_selector", "agree",
            "gdpr_button", "gdpr_selector", "gdpr",
            "privacy_button", "privacy_selector", "privacy",
            // Generic navigation patterns - common knowledge
            "login_button", "login_selector", "submit_button", "submit_selector",
            "next_button", "back_button", "close_button",
            // Too vague
            "button", "selector", "element", "input", "link"
    );

    // Key patterns that indicate useless knowledge (partial matches)
    private static final java.util.regex.Pattern BLOCKED_KEY_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(consent|cookie|gdpr|privacy|accept.*all|agree.*all|popup|modal|overlay|banner)"
    );

    private final KnowledgeRepository repository;

    public KnowledgeService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    /**
     * Checks if a pattern key should be blocked from storage.
     */
    private boolean isBlockedKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalizedKey = key.toLowerCase().trim();
        if (BLOCKED_KEYS.contains(normalizedKey)) {
            return true;
        }
        return BLOCKED_KEY_PATTERN.matcher(normalizedKey).find();
    }

    // ============== Store Patterns ==============

    /**
     * Stores or updates a site pattern.
     *
     * <p>If a pattern with the same domain/type/key already exists, updates stats.
     * Otherwise creates a new pattern.
     *
     * <p>Patterns with blocked keys (consent, cookie, generic navigation) are rejected
     * as they are either handled automatically or too generic to be useful.
     *
     * @param domain  The domain (e.g., "app.example.com")
     * @param type    The pattern type
     * @param key     The pattern key (e.g., "login_button")
     * @param value   The pattern value (e.g., CSS selector)
     * @param runId   The test run that discovered this pattern
     * @param tenantId Optional tenant ID for non-global patterns
     * @return The stored pattern, or empty if validation failed
     */
    public Optional<SitePattern> storePattern(
            String domain,
            PatternType type,
            String key,
            String value,
            UUID runId,
            String tenantId) {

        // Validate key against blocklist
        if (isBlockedKey(key)) {
            log.debug("[KNOWLEDGE] Rejecting blocked pattern key: {}/{}", domain, key);
            return Optional.empty();
        }

        return findOrCreatePattern(domain, type, key, value, runId, tenantId);
    }

    /**
     * Stores a global selector pattern with default settings.
     */
    public Optional<SitePattern> storeSelectorPattern(
            String domain,
            String key,
            String selector,
            UUID runId) {
        return storePattern(domain, PatternType.SELECTOR, key, selector, runId, null);
    }

    /**
     * Stores a timing pattern.
     */
    public Optional<SitePattern> storeTimingPattern(
            String domain,
            String key,
            String timingConfig,
            UUID runId) {
        return storePattern(domain, PatternType.TIMING, key, timingConfig, runId, null);
    }

    /**
     * Stores a quirk/workaround pattern.
     */
    public Optional<SitePattern> storeQuirkPattern(
            String domain,
            String key,
            String description,
            UUID runId) {
        return storePattern(domain, PatternType.QUIRK, key, description, runId, null);
    }

    // ============== Update Stats ==============

    /**
     * Records a successful use of a pattern.
     */
    public void recordPatternSuccess(SitePatternId patternId, int durationMs) {
        repository.updatePatternStats(patternId, true, durationMs);
        log.debug("[KNOWLEDGE] Recorded success for pattern {}", patternId);
    }

    /**
     * Records a failed use of a pattern.
     */
    public void recordPatternFailure(SitePatternId patternId) {
        repository.updatePatternStats(patternId, false, null);
        log.debug("[KNOWLEDGE] Recorded failure for pattern {}", patternId);
    }

    /**
     * Records the result of using a selector alternative.
     */
    public void recordAlternativeResult(UUID alternativeId, boolean success) {
        repository.updateAlternativeStats(alternativeId, success);
    }

    // ============== Query Patterns ==============

    /**
     * Finds all patterns for a domain that the tenant can access.
     */
    @Transactional(readOnly = true)
    public List<SitePattern> findPatterns(String domain, String tenantId) {
        return repository.findPatternsWithAccess(domain, tenantId);
    }

    /**
     * Finds top-rated patterns for a domain.
     */
    @Transactional(readOnly = true)
    public List<SitePattern> findTopPatterns(String domain, int limit) {
        return repository.findTopPatterns(domain, limit);
    }

    /**
     * Finds selector alternatives for a pattern.
     */
    @Transactional(readOnly = true)
    public List<SelectorAlternative> findAlternatives(SitePatternId patternId) {
        return repository.findAlternatives(patternId);
    }

    // ============== Flow Strategies ==============

    /**
     * Stores a flow strategy.
     */
    public Optional<FlowStrategy> storeFlowStrategy(
            String domain,
            String flowName,
            String description,
            List<FlowStep> steps,
            String tenantId) {

        return FlowStrategyFactory.create(
                domain,
                flowName,
                description,
                steps,
                tenantId != null ? Visibility.TENANT : Visibility.GLOBAL,
                tenantId
        ).map(repository::saveStrategy);
    }

    /**
     * Finds flow strategies for a domain and flow name.
     */
    @Transactional(readOnly = true)
    public List<FlowStrategy> findStrategies(String domain, String flowName, String tenantId) {
        return repository.findStrategiesWithAccess(domain, flowName, tenantId);
    }

    // ============== Selector Alternatives ==============

    /**
     * Adds an alternative selector to a pattern.
     */
    public SelectorAlternative addSelectorAlternative(
            SitePatternId patternId,
            SelectorType type,
            String value,
            int priority) {

        SelectorAlternative alternative = new SelectorAlternative(
                UUID.randomUUID(),
                patternId,
                type,
                value,
                priority,
                new BigDecimal("0.50"),
                0,
                0,
                Instant.now()
        );
        return repository.saveAlternative(alternative);
    }

    // ============== Statistics ==============

    /**
     * Gets knowledge contribution stats for a tenant.
     */
    @Transactional(readOnly = true)
    public KnowledgeStats getStats(String tenantId) {
        long contributed = repository.countPatternsContributed(tenantId);
        long accessed = repository.countPatternsAccessed(tenantId);
        long domainsCovered = repository.countDomainsCovered();

        return new KnowledgeStats(contributed, accessed, domainsCovered);
    }

    /**
     * DTO for knowledge statistics.
     */
    public record KnowledgeStats(
            long patternsContributed,
            long patternsAccessed,
            long domainsCovered
    ) {}

    // ============== Helper Methods ==============

    private Optional<SitePattern> findOrCreatePattern(
            String domain,
            PatternType type,
            String key,
            String value,
            UUID runId,
            String tenantId) {

        // Check if pattern already exists
        Optional<SitePattern> existing = repository.findPattern(domain, type, key, tenantId);

        if (existing.isPresent()) {
            // Pattern exists - just return it (stats will be updated via recordSuccess/recordFailure)
            log.debug("[KNOWLEDGE] Found existing pattern: {}/{}/{}", domain, type, key);
            return existing;
        }

        // Create new pattern
        return SitePatternFactory.createGlobal(domain, type, key, value, runId)
                .map(pattern -> {
                    SitePattern saved = repository.savePattern(pattern);
                    log.info("[KNOWLEDGE] Created new pattern: {}/{}/{}", domain, type, key);
                    return saved;
                });
    }
}
