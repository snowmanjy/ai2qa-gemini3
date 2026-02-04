package com.ai2qa.domain.model.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Represents a reusable test flow strategy for common tasks.
 *
 * <p>Flow strategies describe how to accomplish common testing goals
 * like login, checkout, search, etc. They can be domain-specific or generic.
 */
public record FlowStrategy(
        FlowStrategyId id,
        Optional<String> domain,
        String flowName,
        Optional<String> description,
        List<FlowStep> steps,
        int successCount,
        int failureCount,
        Optional<Integer> avgDurationMs,
        Visibility visibility,
        Optional<String> tenantId,
        Instant createdAt,
        Instant updatedAt,
        int version
) {

    /**
     * Calculates the success rate based on success/failure counts.
     */
    public double successRate() {
        int total = successCount + failureCount;
        if (total == 0) {
            return 0.5;
        }
        return (double) successCount / total;
    }

    /**
     * Creates a copy with updated success tracking.
     */
    public FlowStrategy withSuccess(int durationMs) {
        int newSuccessCount = successCount + 1;
        int total = newSuccessCount + failureCount;
        int newAvgDuration = avgDurationMs
                .map(avg -> (avg * (total - 1) + durationMs) / total)
                .orElse(durationMs);

        return new FlowStrategy(
                id, domain, flowName, description, steps,
                newSuccessCount, failureCount,
                Optional.of(newAvgDuration),
                visibility, tenantId, createdAt, Instant.now(),
                version
        );
    }

    /**
     * Creates a copy with updated failure tracking.
     */
    public FlowStrategy withFailure() {
        return new FlowStrategy(
                id, domain, flowName, description, steps,
                successCount, failureCount + 1,
                avgDurationMs,
                visibility, tenantId, createdAt, Instant.now(),
                version
        );
    }

    /**
     * Checks if this is a generic (non-domain-specific) strategy.
     */
    public boolean isGeneric() {
        return domain.isEmpty();
    }

    /**
     * Checks if this strategy applies to the given domain.
     */
    public boolean appliesTo(String targetDomain) {
        return domain.isEmpty() || domain.get().equalsIgnoreCase(targetDomain);
    }
}
