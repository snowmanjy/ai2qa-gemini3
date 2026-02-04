package com.ai2qa.domain.model.knowledge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a site-specific pattern discovered during QA test runs.
 *
 * <p>Patterns include selectors, timing hints, authentication quirks,
 * and other site-specific knowledge that can help future test runs.
 */
public record SitePattern(
        SitePatternId id,
        String domain,
        PatternType type,
        String key,
        String value,
        BigDecimal confidenceScore,
        int successCount,
        int failureCount,
        Optional<Integer> avgDurationMs,
        Optional<Instant> lastSeenAt,
        Instant createdAt,
        Optional<UUID> createdByRunId,
        Visibility visibility,
        Optional<String> tenantId,
        int version
) {

    /**
     * Calculates the success rate based on success/failure counts.
     */
    public BigDecimal successRate() {
        int total = successCount + failureCount;
        if (total == 0) {
            return confidenceScore;
        }
        return BigDecimal.valueOf(successCount)
                .divide(BigDecimal.valueOf(total), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Creates a copy with updated success tracking.
     */
    public SitePattern withSuccess(int durationMs) {
        int newSuccessCount = successCount + 1;
        int total = newSuccessCount + failureCount;
        int newAvgDuration = avgDurationMs
                .map(avg -> (avg * (total - 1) + durationMs) / total)
                .orElse(durationMs);
        BigDecimal newConfidence = calculateConfidence(newSuccessCount, failureCount);

        return new SitePattern(
                id, domain, type, key, value,
                newConfidence, newSuccessCount, failureCount,
                Optional.of(newAvgDuration),
                Optional.of(Instant.now()),
                createdAt, createdByRunId, visibility, tenantId,
                version
        );
    }

    /**
     * Creates a copy with updated failure tracking.
     */
    public SitePattern withFailure() {
        int newFailureCount = failureCount + 1;
        BigDecimal newConfidence = calculateConfidence(successCount, newFailureCount);

        return new SitePattern(
                id, domain, type, key, value,
                newConfidence, successCount, newFailureCount,
                avgDurationMs,
                Optional.of(Instant.now()),
                createdAt, createdByRunId, visibility, tenantId,
                version
        );
    }

    private BigDecimal calculateConfidence(int successes, int failures) {
        int total = successes + failures;
        if (total == 0) {
            return new BigDecimal("0.50");
        }
        // Wilson score lower bound for confidence
        double z = 1.96; // 95% confidence
        double n = total;
        double p = (double) successes / n;
        double denominator = 1 + z * z / n;
        double center = p + z * z / (2 * n);
        double spread = z * Math.sqrt((p * (1 - p) + z * z / (4 * n)) / n);
        double lower = (center - spread) / denominator;
        return BigDecimal.valueOf(Math.max(0, Math.min(1, lower)))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
