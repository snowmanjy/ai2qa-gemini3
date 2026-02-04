package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.SelectorAlternativeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for selector alternative entities.
 */
@Repository
public interface SelectorAlternativeJpaRepository extends JpaRepository<SelectorAlternativeEntity, UUID> {

    /**
     * Finds all alternatives for a site pattern, ordered by priority.
     */
    List<SelectorAlternativeEntity> findBySitePatternIdOrderByPriorityDesc(UUID sitePatternId);

    /**
     * Updates alternative success stats.
     */
    @Modifying
    @Query("""
        UPDATE SelectorAlternativeEntity a
        SET a.successCount = a.successCount + 1,
            a.successRate = CAST((a.successCount + 1.0) / (a.successCount + a.failureCount + 1) AS java.math.BigDecimal)
        WHERE a.id = :id
        """)
    void updateSuccessStats(@Param("id") UUID id);

    /**
     * Updates alternative failure stats.
     */
    @Modifying
    @Query("""
        UPDATE SelectorAlternativeEntity a
        SET a.failureCount = a.failureCount + 1,
            a.successRate = CAST(a.successCount * 1.0 / (a.successCount + a.failureCount + 1) AS java.math.BigDecimal)
        WHERE a.id = :id
        """)
    void updateFailureStats(@Param("id") UUID id);

    /**
     * Deletes all alternatives for a site pattern.
     */
    void deleteBySitePatternId(UUID sitePatternId);
}
