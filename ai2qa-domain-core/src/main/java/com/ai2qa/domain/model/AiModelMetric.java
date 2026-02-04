package com.ai2qa.domain.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Metrics record for AI model operations.
 * Pure data - no validation in constructor per clean architecture.
 *
 * <p>Used for tracking model performance, cost, and accuracy
 * to enable data-driven optimization decisions.
 */
public record AiModelMetric(
        UUID id,
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
        Instant createdAt
) {
    /**
     * Returns the error reason if the operation failed.
     */
    public Optional<String> getErrorReason() {
        return Optional.ofNullable(errorReason);
    }

    /**
     * Returns the test run ID if available.
     */
    public Optional<UUID> getTestRunId() {
        return Optional.ofNullable(testRunId);
    }

    /**
     * Calculates total tokens used (input + output).
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
