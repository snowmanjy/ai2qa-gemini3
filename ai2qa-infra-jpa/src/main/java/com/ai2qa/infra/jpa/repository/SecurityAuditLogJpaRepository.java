package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.SecurityAuditLogEntity;
import com.ai2qa.infra.jpa.entity.SecurityAuditLogEntity.Decision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for SecurityAuditLogEntity.
 */
@Repository
public interface SecurityAuditLogJpaRepository extends JpaRepository<SecurityAuditLogEntity, UUID> {

    /**
     * Finds audit entries for a tenant, ordered by creation time descending.
     */
    Page<SecurityAuditLogEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Finds audit entries by decision type.
     */
    Page<SecurityAuditLogEntity> findByDecisionOrderByCreatedAtDesc(Decision decision, Pageable pageable);

    /**
     * Finds blocked requests for a specific IP.
     */
    List<SecurityAuditLogEntity> findByClientIpAndDecisionOrderByCreatedAtDesc(
            String clientIp, Decision decision);

    /**
     * Finds blocked requests for a specific domain.
     */
    List<SecurityAuditLogEntity> findByTargetDomainAndDecisionOrderByCreatedAtDesc(
            String targetDomain, Decision decision);

    /**
     * Counts blocked requests by IP in a time window (for detecting attacks).
     */
    @Query("SELECT COUNT(s) FROM SecurityAuditLogEntity s " +
           "WHERE s.clientIp = :clientIp AND s.decision = :decision " +
           "AND s.createdAt >= :since")
    long countByClientIpAndDecisionSince(
            @Param("clientIp") String clientIp,
            @Param("decision") Decision decision,
            @Param("since") Instant since);

    /**
     * Counts blocked requests by domain in a time window.
     */
    @Query("SELECT COUNT(s) FROM SecurityAuditLogEntity s " +
           "WHERE s.targetDomain = :domain AND s.decision = :decision " +
           "AND s.createdAt >= :since")
    long countByTargetDomainAndDecisionSince(
            @Param("domain") String domain,
            @Param("decision") Decision decision,
            @Param("since") Instant since);

    /**
     * Counts all requests by decision type in a time window.
     */
    @Query("SELECT COUNT(s) FROM SecurityAuditLogEntity s " +
           "WHERE s.decision = :decision AND s.createdAt >= :since")
    long countByDecisionSince(
            @Param("decision") Decision decision,
            @Param("since") Instant since);

    /**
     * Gets recent blocked requests for monitoring dashboard.
     */
    @Query("SELECT s FROM SecurityAuditLogEntity s " +
           "WHERE s.decision = 'BLOCKED' AND s.createdAt >= :since " +
           "ORDER BY s.createdAt DESC")
    List<SecurityAuditLogEntity> findRecentBlocked(@Param("since") Instant since, Pageable pageable);
}
