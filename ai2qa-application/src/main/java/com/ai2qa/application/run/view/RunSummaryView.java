package com.ai2qa.application.run.view;

import java.util.List;
import java.util.Optional;

/**
 * View model for RunSummary.
 */
public record RunSummaryView(
        String status,
        String goalOverview,
        String outcomeShort,
        Optional<String> failureAnalysis,
        Optional<String> actionableFix,
        List<String> keyAchievements,
        HealthCheckView healthCheck
) {
    /**
     * Diagnostic health check view.
     */
    public record HealthCheckView(
            IssueStatsView networkIssues,
            IssueStatsView consoleIssues,
            String accessibilityScore,
            String accessibilitySummary
    ) {}

    /**
     * Stats for a specific issue type.
     */
    public record IssueStatsView(
            int count,
            String summary
    ) {}
}
