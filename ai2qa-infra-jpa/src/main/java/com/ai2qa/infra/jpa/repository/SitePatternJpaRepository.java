package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.SitePatternEntity;
import com.ai2qa.infra.jpa.entity.SitePatternEntity.PatternTypeEnum;
import com.ai2qa.infra.jpa.entity.SitePatternEntity.VisibilityEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for site pattern entities.
 */
@Repository
public interface SitePatternJpaRepository extends JpaRepository<SitePatternEntity, UUID> {

    /**
     * Finds a pattern by domain, type, key, and tenant.
     */
    @Query("""
        SELECT p FROM SitePatternEntity p
        WHERE p.domain = :domain
        AND p.patternType = :patternType
        AND p.patternKey = :patternKey
        AND (p.tenantId = :tenantId OR p.tenantId IS NULL)
        """)
    Optional<SitePatternEntity> findByDomainAndTypeAndKeyAndTenant(
            @Param("domain") String domain,
            @Param("patternType") PatternTypeEnum patternType,
            @Param("patternKey") String patternKey,
            @Param("tenantId") String tenantId);

    /**
     * Finds all patterns for a domain.
     */
    List<SitePatternEntity> findByDomainOrderByConfidenceScoreDesc(String domain);

    /**
     * Finds patterns accessible to a tenant (GLOBAL + their TENANT patterns).
     */
    @Query("""
        SELECT p FROM SitePatternEntity p
        WHERE p.domain = :domain
        AND (p.visibility = 'GLOBAL'
             OR (p.visibility = 'TENANT' AND p.tenantId = :tenantId))
        ORDER BY p.confidenceScore DESC
        """)
    List<SitePatternEntity> findPatternsWithAccess(
            @Param("domain") String domain,
            @Param("tenantId") String tenantId);

    /**
     * Finds top patterns by confidence score.
     */
    @Query("""
        SELECT p FROM SitePatternEntity p
        WHERE p.domain = :domain
        AND p.visibility = 'GLOBAL'
        ORDER BY p.confidenceScore DESC
        LIMIT :limit
        """)
    List<SitePatternEntity> findTopPatterns(
            @Param("domain") String domain,
            @Param("limit") int limit);

    /**
     * Updates pattern success stats.
     */
    @Modifying
    @Query("""
        UPDATE SitePatternEntity p
        SET p.successCount = p.successCount + 1,
            p.lastSeenAt = CURRENT_TIMESTAMP,
            p.avgDurationMs = CASE
                WHEN p.avgDurationMs IS NULL THEN :durationMs
                ELSE (p.avgDurationMs * (p.successCount + p.failureCount) + :durationMs)
                     / (p.successCount + p.failureCount + 1)
            END
        WHERE p.id = :id
        """)
    void updateSuccessStats(@Param("id") UUID id, @Param("durationMs") Integer durationMs);

    /**
     * Updates pattern failure stats.
     */
    @Modifying
    @Query("""
        UPDATE SitePatternEntity p
        SET p.failureCount = p.failureCount + 1,
            p.lastSeenAt = CURRENT_TIMESTAMP
        WHERE p.id = :id
        """)
    void updateFailureStats(@Param("id") UUID id);

    /**
     * Counts patterns contributed by a tenant.
     */
    @Query("""
        SELECT COUNT(p) FROM SitePatternEntity p
        WHERE p.tenantId = :tenantId
        """)
    long countByTenantId(@Param("tenantId") String tenantId);

    /**
     * Counts all unique domains with patterns.
     */
    @Query("SELECT COUNT(DISTINCT p.domain) FROM SitePatternEntity p")
    long countUniqueDomains();
}
