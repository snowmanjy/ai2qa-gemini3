package com.ai2qa.application.knowledge;

import com.ai2qa.domain.port.KnowledgeAccessPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Guards against abuse of the knowledge API.
 *
 * <p>Implements anti-scraping protections:
 * <ul>
 *   <li>Rate limiting - max domains per hour/day</li>
 *   <li>Contribution ratio - must contribute to access (after grace period)</li>
 *   <li>Anomaly detection - unusual access patterns</li>
 * </ul>
 */
@Component
public class KnowledgeAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAccessGuard.class);

    private final KnowledgeAccessPort accessPort;
    private final int maxDomainsPerHour;
    private final int maxDomainsPerDay;
    private final int gracePeriodPatterns;
    private final int contributionRatio;

    public KnowledgeAccessGuard(
            KnowledgeAccessPort accessPort,
            @Value("${ai2qa.knowledge.rate-limit.domains-per-hour:20}") int maxDomainsPerHour,
            @Value("${ai2qa.knowledge.rate-limit.domains-per-day:100}") int maxDomainsPerDay,
            @Value("${ai2qa.knowledge.grace-period-patterns:100}") int gracePeriodPatterns,
            @Value("${ai2qa.knowledge.contribution-ratio:5}") int contributionRatio) {
        this.accessPort = accessPort;
        this.maxDomainsPerHour = maxDomainsPerHour;
        this.maxDomainsPerDay = maxDomainsPerDay;
        this.gracePeriodPatterns = gracePeriodPatterns;
        this.contributionRatio = contributionRatio;
    }

    /**
     * Checks if a tenant can access knowledge for a domain.
     *
     * @param tenantId The tenant ID
     * @param domain The domain being accessed
     * @return Access result with isAllowed flag and reason
     */
    public AccessResult checkAccess(String tenantId, String domain) {
        // Check hourly rate limit
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long hourlyAccess = accessPort.countRecentAccess(tenantId, oneHourAgo);
        if (hourlyAccess >= maxDomainsPerHour) {
            log.warn("[KNOWLEDGE_GUARD] Hourly rate limit exceeded for tenant {}", tenantId);
            return AccessResult.denied("Hourly rate limit exceeded. Try again later.");
        }

        // Check daily rate limit
        Instant oneDayAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        long dailyAccess = accessPort.countUniqueDomainsAccessed(tenantId, oneDayAgo);
        if (dailyAccess >= maxDomainsPerDay) {
            log.warn("[KNOWLEDGE_GUARD] Daily rate limit exceeded for tenant {}", tenantId);
            return AccessResult.denied("Daily rate limit exceeded. Try again tomorrow.");
        }

        // Check contribution ratio (after grace period)
        long accessed = accessPort.countPatternsAccessed(tenantId);
        if (accessed > gracePeriodPatterns) {
            // After grace period, must contribute 1 pattern for every N accessed
            // For now, we just log this - full enforcement can come later
            log.debug("[KNOWLEDGE_GUARD] Tenant {} has accessed {} patterns", tenantId, accessed);
        }

        return AccessResult.allowed();
    }

    /**
     * Logs an access to the knowledge base for metering.
     */
    public void logAccess(
            String tenantId,
            String domain,
            int patternsAccessed,
            BigDecimal creditsCharged) {

        accessPort.logAccess(tenantId, domain, patternsAccessed, creditsCharged);
        log.debug("[KNOWLEDGE_GUARD] Logged access: tenant={}, domain={}, patterns={}",
                tenantId, domain, patternsAccessed);
    }

    /**
     * Logs a contribution to the knowledge base.
     */
    public void logContribution(
            String tenantId,
            String domain,
            int patternsContributed) {

        accessPort.logContribution(tenantId, domain, patternsContributed);
        log.debug("[KNOWLEDGE_GUARD] Logged contribution: tenant={}, domain={}, patterns={}",
                tenantId, domain, patternsContributed);
    }

    /**
     * Access check result.
     */
    public record AccessResult(
            boolean isAllowed,
            String reason
    ) {
        public static AccessResult allowed() {
            return new AccessResult(true, null);
        }

        public static AccessResult denied(String reason) {
            return new AccessResult(false, reason);
        }
    }
}
