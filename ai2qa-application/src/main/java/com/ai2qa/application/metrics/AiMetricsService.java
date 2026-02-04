package com.ai2qa.application.metrics;

import com.ai2qa.domain.factory.AiModelMetricFactory;
import com.ai2qa.domain.model.AiModelMetric;
import com.ai2qa.domain.model.AiOperationType;
import com.ai2qa.domain.repository.AiModelMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Application service for recording AI model metrics.
 *
 * <p>Metrics are recorded asynchronously to avoid impacting
 * the main request flow.
 */
@Service
public class AiMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AiMetricsService.class);

    private final AiModelMetricRepository repository;
    private final Clock clock;

    public AiMetricsService(AiModelMetricRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records a successful element find operation.
     *
     * @param tenantId      The tenant ID
     * @param testRunId     The test run ID (may be null)
     * @param modelProvider The AI provider (e.g., "vertex-ai")
     * @param modelName     The model name (e.g., "gemini-2.0-flash")
     * @param inputTokens   Tokens sent to model
     * @param outputTokens  Tokens received from model
     * @param latencyMs     Response time in milliseconds
     * @param fallbackUsed  Whether this was a fallback call
     */
    @Async
    public void recordElementFind(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean fallbackUsed) {

        AiModelMetric metric = AiModelMetricFactory.forElementFind(
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                inputTokens,
                outputTokens,
                latencyMs,
                fallbackUsed,
                clock.instant()
        );

        saveMetric(metric);
    }

    /**
     * Records a failed element find operation.
     */
    @Async
    public void recordElementFindFailure(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean fallbackUsed,
            String errorReason) {

        AiModelMetric metric = AiModelMetricFactory.forElementFindFailure(
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                inputTokens,
                outputTokens,
                latencyMs,
                fallbackUsed,
                errorReason,
                clock.instant()
        );

        saveMetric(metric);
    }

    /**
     * Records a plan generation operation.
     */
    @Async
    public void recordPlanGeneration(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            String errorReason) {

        AiModelMetric metric = AiModelMetricFactory.forPlanGeneration(
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                inputTokens,
                outputTokens,
                latencyMs,
                success,
                errorReason,
                clock.instant()
        );

        saveMetric(metric);
    }

    /**
     * Records a repair plan operation.
     */
    @Async
    public void recordRepairPlan(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            String errorReason) {

        AiModelMetric metric = AiModelMetricFactory.forRepairPlan(
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                inputTokens,
                outputTokens,
                latencyMs,
                success,
                errorReason,
                clock.instant()
        );

        saveMetric(metric);
    }

    /**
     * Gets all metrics for a tenant.
     */
    public List<AiModelMetric> getMetricsForTenant(String tenantId) {
        return repository.findByTenantId(tenantId);
    }

    /**
     * Gets all metrics for a test run.
     */
    public List<AiModelMetric> getMetricsForTestRun(UUID testRunId) {
        return repository.findByTestRunId(testRunId);
    }

    /**
     * Gets the fallback rate for a tenant.
     *
     * @return Percentage of calls that used fallback (0-100)
     */
    public double getFallbackRate(String tenantId) {
        long total = repository.countByTenantIdAndModelProvider(tenantId, "vertex-ai")
                + repository.countByTenantIdAndModelProvider(tenantId, "anthropic")
                + repository.countByTenantIdAndModelProvider(tenantId, "openai");

        if (total == 0) {
            return 0.0;
        }

        long fallbacks = repository.countFallbacksByTenantId(tenantId);
        return (fallbacks * 100.0) / total;
    }

    private void saveMetric(AiModelMetric metric) {
        try {
            repository.save(metric);
            log.debug("Recorded AI metric: {} {} {} tokens, {}ms",
                    metric.operationType(),
                    metric.modelProvider(),
                    metric.totalTokens(),
                    metric.latencyMs());
        } catch (Exception e) {
            // Don't fail the main flow if metrics recording fails
            log.warn("Failed to record AI metric: {}", e.getMessage());
        }
    }
}
