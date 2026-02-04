package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.AiModelMetric;
import com.ai2qa.domain.model.AiOperationType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Factory for creating AI model metric entries.
 * Provides type-safe factory methods for different operation types.
 */
public final class AiModelMetricFactory {

    private AiModelMetricFactory() {
        // utility class
    }

    /**
     * Creates a metric with full validation.
     *
     * @return Optional containing the metric, or empty if validation fails
     */
    public static Optional<AiModelMetric> create(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            AiOperationType operationType,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            boolean fallbackUsed,
            String errorReason,
            Instant now) {

        return Optional.ofNullable(tenantId)
                .filter(t -> !t.isBlank())
                .filter(t -> modelProvider != null && !modelProvider.isBlank())
                .filter(t -> operationType != null)
                .filter(t -> now != null)
                .map(t -> new AiModelMetric(
                        UUID.randomUUID(),
                        t,
                        testRunId,
                        modelProvider,
                        modelName,
                        operationType,
                        inputTokens,
                        outputTokens,
                        latencyMs,
                        success,
                        fallbackUsed,
                        errorReason,
                        now
                ));
    }

    /**
     * Creates a successful metric for element finding.
     */
    public static AiModelMetric forElementFind(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean fallbackUsed,
            Instant now) {
        return new AiModelMetric(
                UUID.randomUUID(),
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                AiOperationType.ELEMENT_FIND,
                inputTokens,
                outputTokens,
                latencyMs,
                true,
                fallbackUsed,
                null,
                now
        );
    }

    /**
     * Creates a failed metric for element finding.
     */
    public static AiModelMetric forElementFindFailure(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean fallbackUsed,
            String errorReason,
            Instant now) {
        return new AiModelMetric(
                UUID.randomUUID(),
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                AiOperationType.ELEMENT_FIND,
                inputTokens,
                outputTokens,
                latencyMs,
                false,
                fallbackUsed,
                errorReason,
                now
        );
    }

    /**
     * Creates a metric for plan generation.
     */
    public static AiModelMetric forPlanGeneration(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            String errorReason,
            Instant now) {
        return new AiModelMetric(
                UUID.randomUUID(),
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                AiOperationType.PLAN_GENERATION,
                inputTokens,
                outputTokens,
                latencyMs,
                success,
                false,  // no fallback for plan generation yet
                errorReason,
                now
        );
    }

    /**
     * Creates a metric for repair plan generation.
     */
    public static AiModelMetric forRepairPlan(
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            String errorReason,
            Instant now) {
        return new AiModelMetric(
                UUID.randomUUID(),
                tenantId,
                testRunId,
                modelProvider,
                modelName,
                AiOperationType.REPAIR_PLAN,
                inputTokens,
                outputTokens,
                latencyMs,
                success,
                false,
                errorReason,
                now
        );
    }
}
