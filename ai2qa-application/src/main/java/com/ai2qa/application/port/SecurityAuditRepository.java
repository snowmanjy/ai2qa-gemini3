package com.ai2qa.application.port;

import java.time.Instant;

/**
 * Port interface for security audit logging persistence.
 *
 * <p>Implementations should store audit logs in a persistent store
 * for security monitoring and compliance.
 */
public interface SecurityAuditRepository {

    /**
     * Saves a security audit log entry.
     *
     * @param entry The audit entry to save
     */
    void save(SecurityAuditEntry entry);

    /**
     * Counts entries by decision type since a given time.
     *
     * @param decision The decision type to count
     * @param since    Count entries created after this time
     * @return The count of matching entries
     */
    long countByDecisionSince(Decision decision, Instant since);

    /**
     * Security audit entry for persistence.
     */
    record SecurityAuditEntry(
            String tenantId,
            String clientIp,
            String targetUrl,
            String targetDomain,
            Decision decision,
            String blockReason,
            int riskScore,
            String userAgent,
            String requestId,
            Instant createdAt
    ) {}

    /**
     * Decision types for audit logging.
     */
    enum Decision {
        ALLOWED,
        BLOCKED,
        RATE_LIMITED
    }
}
