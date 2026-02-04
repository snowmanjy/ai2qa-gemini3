package com.ai2qa.infra.jpa.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for selector cache.
 */
@Repository
public interface SelectorCacheJpaRepository extends JpaRepository<SelectorCacheEntity, UUID> {

    /**
     * Finds a cached selector by tenant, goal hash, and URL pattern.
     */
    Optional<SelectorCacheEntity> findByTenantIdAndGoalHashAndUrlPattern(
            String tenantId,
            String goalHash,
            String urlPattern
    );

    /**
     * Checks if a cached selector exists.
     */
    boolean existsByTenantIdAndGoalHashAndUrlPattern(
            String tenantId,
            String goalHash,
            String urlPattern
    );

    /**
     * Deletes a cached selector by tenant, goal hash, and URL pattern.
     */
    @Modifying
    @Query("DELETE FROM SelectorCacheEntity e " +
           "WHERE e.tenantId = :tenantId " +
           "AND e.goalHash = :goalHash " +
           "AND e.urlPattern = :urlPattern")
    int deleteByTenantIdAndGoalHashAndUrlPattern(
            @Param("tenantId") String tenantId,
            @Param("goalHash") String goalHash,
            @Param("urlPattern") String urlPattern
    );

    /**
     * Deletes stale cache entries.
     */
    @Modifying
    @Query("DELETE FROM SelectorCacheEntity e WHERE e.lastUsedAt < :cutoff")
    int deleteStaleEntries(@Param("cutoff") Instant cutoff);

    /**
     * Counts entries for a tenant.
     */
    long countByTenantId(String tenantId);
}
