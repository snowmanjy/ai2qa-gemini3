package com.ai2qa.domain.model.knowledge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an alternative selector for finding the same element.
 *
 * <p>Multiple selectors can target the same element with varying
 * reliability and performance characteristics.
 */
public record SelectorAlternative(
        UUID id,
        SitePatternId sitePatternId,
        SelectorType type,
        String value,
        int priority,
        BigDecimal successRate,
        int successCount,
        int failureCount,
        Instant createdAt
) {

    /**
     * Creates a copy with updated success tracking.
     */
    public SelectorAlternative withSuccess() {
        int newSuccessCount = successCount + 1;
        BigDecimal newRate = calculateSuccessRate(newSuccessCount, failureCount);
        return new SelectorAlternative(
                id, sitePatternId, type, value,
                priority, newRate, newSuccessCount, failureCount,
                createdAt
        );
    }

    /**
     * Creates a copy with updated failure tracking.
     */
    public SelectorAlternative withFailure() {
        int newFailureCount = failureCount + 1;
        BigDecimal newRate = calculateSuccessRate(successCount, newFailureCount);
        return new SelectorAlternative(
                id, sitePatternId, type, value,
                priority, newRate, successCount, newFailureCount,
                createdAt
        );
    }

    /**
     * Creates a copy with updated priority.
     */
    public SelectorAlternative withPriority(int newPriority) {
        return new SelectorAlternative(
                id, sitePatternId, type, value,
                newPriority, successRate, successCount, failureCount,
                createdAt
        );
    }

    private BigDecimal calculateSuccessRate(int successes, int failures) {
        int total = successes + failures;
        if (total == 0) {
            return new BigDecimal("0.50");
        }
        return BigDecimal.valueOf(successes)
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }
}
