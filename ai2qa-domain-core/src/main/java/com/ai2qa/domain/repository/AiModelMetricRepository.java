package com.ai2qa.domain.repository;

import com.ai2qa.domain.model.AiModelMetric;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for AI model metrics.
 */
public interface AiModelMetricRepository {

    /**
     * Saves a metric entry.
     *
     * @param metric The metric to save
     * @return The saved metric
     */
    AiModelMetric save(AiModelMetric metric);

    /**
     * Saves multiple metric entries.
     *
     * @param metrics The metrics to save
     */
    void saveAll(List<AiModelMetric> metrics);

    /**
     * Finds all metrics for a tenant, ordered by creation time descending.
     *
     * @param tenantId The tenant ID
     * @return List of metrics, most recent first
     */
    List<AiModelMetric> findByTenantId(String tenantId);

    /**
     * Finds all metrics for a test run.
     *
     * @param testRunId The test run ID
     * @return List of metrics for the test run
     */
    List<AiModelMetric> findByTestRunId(UUID testRunId);

    /**
     * Finds metrics within a date range.
     *
     * @param start Start of date range (inclusive)
     * @param end   End of date range (exclusive)
     * @return List of metrics in the range
     */
    List<AiModelMetric> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * Counts metrics by model provider for a tenant.
     *
     * @param tenantId The tenant ID
     * @param modelProvider The model provider (e.g., "vertex-ai", "anthropic")
     * @return Count of metrics for this provider
     */
    long countByTenantIdAndModelProvider(String tenantId, String modelProvider);

    /**
     * Counts fallback usage for a tenant.
     *
     * @param tenantId The tenant ID
     * @return Count of metrics where fallback was used
     */
    long countFallbacksByTenantId(String tenantId);

    // ==================== Global Admin Aggregation Methods ====================

    /**
     * Counts total metrics in a date range.
     */
    long countInRange(Instant start, Instant end);

    /**
     * Counts successful metrics in a date range.
     */
    long countSuccessInRange(Instant start, Instant end);

    /**
     * Counts fallback usage in a date range.
     */
    long countFallbacksInRange(Instant start, Instant end);

    /**
     * Gets aggregated metrics by model name.
     *
     * @return List of [modelName, inputTokens, outputTokens, callCount, avgLatencyMs]
     */
    List<ModelAggregation> aggregateByModelInRange(Instant start, Instant end);

    /**
     * Gets aggregated metrics by operation type.
     *
     * @return List of [operationType, totalCalls, successCalls, avgLatencyMs]
     */
    List<OperationAggregation> aggregateByOperationInRange(Instant start, Instant end);

    /**
     * Gets all latencies in range, ordered ascending for percentile calculation.
     */
    List<Integer> findLatenciesInRange(Instant start, Instant end);

    /**
     * Gets hourly aggregated data for time series charts.
     */
    List<HourlyAggregation> getHourlyAggregates(Instant start, Instant end);

    // Aggregation result records
    record ModelAggregation(
            String modelName,
            long inputTokens,
            long outputTokens,
            long callCount,
            double avgLatencyMs
    ) {}

    record OperationAggregation(
            String operationType,
            long totalCalls,
            long successCalls,
            double avgLatencyMs
    ) {}

    record HourlyAggregation(
            Instant hour,
            long totalCalls,
            long inputTokens,
            long outputTokens,
            long successCalls
    ) {}
}
