package com.ai2qa.application.run.view;

import java.time.Instant;
import java.util.Optional;

/**
 * Read model for test run responses.
 */
public record TestRunView(
        String id,
        String targetUrl,
        String persona,
        String status,
        String executionMode,
        Instant createdAt,
        Optional<Instant> completedAt,
        Optional<String> failureReason,
        Optional<RunSummaryView> summary,
        String summaryStatus,
        int progressPercent,
        int executedStepCount,
        int totalStepCount
) {
    public TestRunView {
        if (completedAt == null) {
            completedAt = Optional.empty();
        }
        if (failureReason == null) {
            failureReason = Optional.empty();
        }
        if (summary == null) {
            summary = Optional.empty();
        }
    }
}
