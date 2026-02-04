package com.ai2qa.domain.repository;

import com.ai2qa.domain.model.knowledge.FlowStrategy;
import com.ai2qa.domain.model.knowledge.FlowStrategyId;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorAlternative;
import com.ai2qa.domain.model.knowledge.SitePattern;
import com.ai2qa.domain.model.knowledge.SitePatternId;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for QA knowledge operations.
 */
public interface KnowledgeRepository {

    // ============== Site Patterns ==============

    /**
     * Saves a site pattern, creating or updating as needed.
     */
    SitePattern savePattern(SitePattern pattern);

    /**
     * Finds a pattern by its ID.
     */
    Optional<SitePattern> findPatternById(SitePatternId id);

    /**
     * Finds a pattern by domain, type, and key.
     */
    Optional<SitePattern> findPattern(String domain, PatternType type, String key, String tenantId);

    /**
     * Finds all patterns for a domain.
     */
    List<SitePattern> findPatternsByDomain(String domain);

    /**
     * Finds all patterns for a domain with tenant access.
     * Returns GLOBAL patterns plus TENANT patterns for the given tenant.
     */
    List<SitePattern> findPatternsWithAccess(String domain, String tenantId);

    /**
     * Finds top patterns by confidence score for a domain.
     */
    List<SitePattern> findTopPatterns(String domain, int limit);

    /**
     * Updates success/failure counts for a pattern.
     */
    void updatePatternStats(SitePatternId id, boolean success, Integer durationMs);

    // ============== Selector Alternatives ==============

    /**
     * Saves a selector alternative.
     */
    SelectorAlternative saveAlternative(SelectorAlternative alternative);

    /**
     * Finds all alternatives for a site pattern.
     */
    List<SelectorAlternative> findAlternatives(SitePatternId sitePatternId);

    /**
     * Updates success/failure counts for an alternative.
     */
    void updateAlternativeStats(java.util.UUID alternativeId, boolean success);

    // ============== Flow Strategies ==============

    /**
     * Saves a flow strategy.
     */
    FlowStrategy saveStrategy(FlowStrategy strategy);

    /**
     * Finds a flow strategy by ID.
     */
    Optional<FlowStrategy> findStrategyById(FlowStrategyId id);

    /**
     * Finds strategies for a domain and flow name.
     */
    List<FlowStrategy> findStrategies(String domain, String flowName);

    /**
     * Finds strategies with tenant access.
     */
    List<FlowStrategy> findStrategiesWithAccess(String domain, String flowName, String tenantId);

    /**
     * Updates success/failure counts for a strategy.
     */
    void updateStrategyStats(FlowStrategyId id, boolean success, Integer durationMs);

    // ============== Statistics ==============

    /**
     * Counts patterns contributed by a tenant.
     */
    long countPatternsContributed(String tenantId);

    /**
     * Counts patterns accessed by a tenant.
     */
    long countPatternsAccessed(String tenantId);

    /**
     * Counts unique domains with patterns.
     */
    long countDomainsCovered();
}
