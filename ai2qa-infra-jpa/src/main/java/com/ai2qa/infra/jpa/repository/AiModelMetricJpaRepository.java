package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.AiModelMetricEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for AiModelMetricEntity.
 */
@Repository
public interface AiModelMetricJpaRepository extends JpaRepository<AiModelMetricEntity, UUID> {

    /**
     * Finds all metrics for a tenant, ordered by creation time descending.
     *
     * @param tenantId The tenant ID.
     * @return List of metrics, most recent first.
     */
    List<AiModelMetricEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);

    /**
     * Finds all metrics for a test run.
     *
     * @param testRunId The test run ID.
     * @return List of metrics for the test run.
     */
    List<AiModelMetricEntity> findByTestRunId(UUID testRunId);

    /**
     * Finds metrics within a date range.
     *
     * @param start Start of date range (inclusive).
     * @param end   End of date range (exclusive).
     * @return List of metrics in the range.
     */
    List<AiModelMetricEntity> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Counts metrics by tenant and model provider.
     *
     * @param tenantId      The tenant ID.
     * @param modelProvider The model provider.
     * @return Count of metrics.
     */
    long countByTenantIdAndModelProvider(String tenantId, String modelProvider);

    /**
     * Counts fallback usage for a tenant.
     *
     * @param tenantId     The tenant ID.
     * @param fallbackUsed Should be true.
     * @return Count of metrics where fallback was used.
     */
    long countByTenantIdAndFallbackUsed(String tenantId, boolean fallbackUsed);

    // ==================== Global Admin Queries ====================

    /**
     * Counts total metrics in a date range.
     */
    long countByCreatedAtBetween(Instant start, Instant end);

    /**
     * Counts successful metrics in a date range.
     */
    long countByCreatedAtBetweenAndSuccess(Instant start, Instant end, boolean success);

    /**
     * Counts fallback usage in a date range.
     */
    long countByCreatedAtBetweenAndFallbackUsed(Instant start, Instant end, boolean fallbackUsed);

    /**
     * Aggregates token usage by model in a date range.
     */
    @Query("SELECT m.modelName, SUM(m.inputTokens), SUM(m.outputTokens), COUNT(m), AVG(m.latencyMs) " +
            "FROM AiModelMetricEntity m " +
            "WHERE m.createdAt >= :start AND m.createdAt < :end " +
            "GROUP BY m.modelName")
    List<Object[]> aggregateByModelInRange(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Aggregates metrics by operation type in a date range.
     */
    @Query("SELECT m.operationType, COUNT(m), SUM(CASE WHEN m.success = true THEN 1 ELSE 0 END), AVG(m.latencyMs) " +
            "FROM AiModelMetricEntity m " +
            "WHERE m.createdAt >= :start AND m.createdAt < :end " +
            "GROUP BY m.operationType")
    List<Object[]> aggregateByOperationTypeInRange(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Gets latency percentiles approximation (ordered latencies).
     */
    @Query("SELECT m.latencyMs FROM AiModelMetricEntity m " +
            "WHERE m.createdAt >= :start AND m.createdAt < :end " +
            "ORDER BY m.latencyMs")
    List<Integer> findLatenciesInRange(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Gets hourly aggregates for time series chart.
     */
    @Query(value = "SELECT date_trunc('hour', created_at) as hour, " +
            "COUNT(*) as total_calls, " +
            "SUM(input_tokens) as input_tokens, " +
            "SUM(output_tokens) as output_tokens, " +
            "SUM(CASE WHEN success = true THEN 1 ELSE 0 END) as success_count " +
            "FROM ai_model_metrics " +
            "WHERE created_at >= :start AND created_at < :end " +
            "GROUP BY date_trunc('hour', created_at) " +
            "ORDER BY hour", nativeQuery = true)
    List<Object[]> getHourlyAggregates(@Param("start") Instant start, @Param("end") Instant end);
}
