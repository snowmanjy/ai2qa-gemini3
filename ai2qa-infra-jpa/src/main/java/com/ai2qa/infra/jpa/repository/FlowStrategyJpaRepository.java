package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.FlowStrategyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for flow strategy entities.
 */
@Repository
public interface FlowStrategyJpaRepository extends JpaRepository<FlowStrategyEntity, UUID> {

    /**
     * Finds strategies by domain and flow name.
     */
    @Query("""
        SELECT f FROM FlowStrategyEntity f
        WHERE (f.domain = :domain OR f.domain IS NULL)
        AND f.flowName = :flowName
        ORDER BY f.successCount DESC
        """)
    List<FlowStrategyEntity> findByDomainAndFlowName(
            @Param("domain") String domain,
            @Param("flowName") String flowName);

    /**
     * Finds strategies accessible to a tenant.
     */
    @Query("""
        SELECT f FROM FlowStrategyEntity f
        WHERE (f.domain = :domain OR f.domain IS NULL)
        AND f.flowName = :flowName
        AND (f.visibility = 'GLOBAL'
             OR (f.visibility = 'TENANT' AND f.tenantId = :tenantId))
        ORDER BY f.successCount DESC
        """)
    List<FlowStrategyEntity> findStrategiesWithAccess(
            @Param("domain") String domain,
            @Param("flowName") String flowName,
            @Param("tenantId") String tenantId);

    /**
     * Updates strategy success stats.
     */
    @Modifying
    @Query("""
        UPDATE FlowStrategyEntity f
        SET f.successCount = f.successCount + 1,
            f.updatedAt = CURRENT_TIMESTAMP,
            f.avgDurationMs = CASE
                WHEN f.avgDurationMs IS NULL THEN :durationMs
                ELSE (f.avgDurationMs * (f.successCount + f.failureCount) + :durationMs)
                     / (f.successCount + f.failureCount + 1)
            END
        WHERE f.id = :id
        """)
    void updateSuccessStats(@Param("id") UUID id, @Param("durationMs") Integer durationMs);

    /**
     * Updates strategy failure stats.
     */
    @Modifying
    @Query("""
        UPDATE FlowStrategyEntity f
        SET f.failureCount = f.failureCount + 1,
            f.updatedAt = CURRENT_TIMESTAMP
        WHERE f.id = :id
        """)
    void updateFailureStats(@Param("id") UUID id);
}
