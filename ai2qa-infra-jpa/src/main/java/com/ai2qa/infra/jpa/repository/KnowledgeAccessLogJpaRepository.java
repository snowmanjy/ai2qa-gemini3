package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.KnowledgeAccessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA repository for knowledge access log entities.
 */
@Repository
public interface KnowledgeAccessLogJpaRepository extends JpaRepository<KnowledgeAccessLogEntity, UUID> {

    /**
     * Counts patterns accessed by a tenant.
     */
    @Query("""
        SELECT COALESCE(SUM(l.patternsAccessed), 0) FROM KnowledgeAccessLogEntity l
        WHERE l.tenantId = :tenantId
        """)
    long countPatternsAccessed(@Param("tenantId") String tenantId);

    /**
     * Counts access log entries in the last hour for rate limiting.
     */
    @Query("""
        SELECT COUNT(l) FROM KnowledgeAccessLogEntity l
        WHERE l.tenantId = :tenantId
        AND l.accessType = 'RENT'
        AND l.accessedAt > :since
        """)
    long countRecentAccess(@Param("tenantId") String tenantId, @Param("since") Instant since);

    /**
     * Counts unique domains accessed by a tenant in the last 24 hours.
     */
    @Query("""
        SELECT COUNT(DISTINCT l.domain) FROM KnowledgeAccessLogEntity l
        WHERE l.tenantId = :tenantId
        AND l.accessType = 'RENT'
        AND l.accessedAt > :since
        AND l.domain IS NOT NULL
        """)
    long countUniqueDomainsAccessed(@Param("tenantId") String tenantId, @Param("since") Instant since);
}
