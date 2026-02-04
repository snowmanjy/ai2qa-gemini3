package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.application.port.SecurityAuditRepository;
import com.ai2qa.infra.jpa.entity.SecurityAuditLogEntity;
import com.ai2qa.infra.jpa.repository.SecurityAuditLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA adapter implementing the SecurityAuditRepository port.
 *
 * <p>Persists security audit logs to the database.
 */
@Component
public class SecurityAuditRepositoryJpaAdapter implements SecurityAuditRepository {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditRepositoryJpaAdapter.class);

    private final SecurityAuditLogJpaRepository jpaRepository;

    public SecurityAuditRepositoryJpaAdapter(SecurityAuditLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public void save(SecurityAuditEntry entry) {
        SecurityAuditLogEntity entity = new SecurityAuditLogEntity(
                UUID.randomUUID(),
                entry.tenantId(),
                entry.clientIp(),
                entry.targetUrl(),
                entry.targetDomain(),
                mapDecision(entry.decision()),
                entry.blockReason(),
                entry.riskScore(),
                entry.userAgent(),
                entry.requestId(),
                entry.createdAt()
        );
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByDecisionSince(Decision decision, Instant since) {
        return jpaRepository.countByDecisionSince(mapDecision(decision), since);
    }

    /**
     * Maps port Decision enum to JPA entity Decision enum.
     */
    private SecurityAuditLogEntity.Decision mapDecision(Decision decision) {
        return switch (decision) {
            case ALLOWED -> SecurityAuditLogEntity.Decision.ALLOWED;
            case BLOCKED -> SecurityAuditLogEntity.Decision.BLOCKED;
            case RATE_LIMITED -> SecurityAuditLogEntity.Decision.RATE_LIMITED;
        };
    }
}
