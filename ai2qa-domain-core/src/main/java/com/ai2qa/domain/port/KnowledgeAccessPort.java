package com.ai2qa.domain.port;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Port for knowledge access logging and rate limiting.
 */
public interface KnowledgeAccessPort {

    /**
     * Counts recent access to knowledge by a tenant.
     */
    long countRecentAccess(String tenantId, Instant since);

    /**
     * Counts unique domains accessed by a tenant since a given time.
     */
    long countUniqueDomainsAccessed(String tenantId, Instant since);

    /**
     * Counts total patterns accessed by a tenant.
     */
    long countPatternsAccessed(String tenantId);

    /**
     * Logs a knowledge access (RENT) event.
     */
    void logAccess(String tenantId, String domain, int patternsAccessed, BigDecimal creditsCharged);

    /**
     * Logs a knowledge contribution (LEARN) event.
     */
    void logContribution(String tenantId, String domain, int patternsContributed);
}
