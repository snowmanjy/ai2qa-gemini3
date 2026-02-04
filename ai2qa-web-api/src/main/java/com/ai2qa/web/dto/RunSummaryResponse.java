package com.ai2qa.web.dto;

import com.ai2qa.application.run.view.RunSummaryView;

import java.util.List;
import java.util.Optional;

/**
 * API response DTO for RunSummary.
 */
public record RunSummaryResponse(
        String status,
        String goalOverview,
        String outcomeShort,
        Optional<String> failureAnalysis,
        Optional<String> actionableFix,
        List<String> keyAchievements,
        HealthCheckResponse healthCheck
) {
    /**
     * Diagnostic health check response.
     */
    public record HealthCheckResponse(
            IssueStatsResponse networkIssues,
            IssueStatsResponse consoleIssues,
            String accessibilityScore,
            String accessibilitySummary
    ) {}

    /**
     * Stats for a specific issue type.
     */
    public record IssueStatsResponse(
            int count,
            String summary
    ) {}

    public static RunSummaryResponse from(RunSummaryView view) {
        return new RunSummaryResponse(
                view.status(),
                view.goalOverview(),
                view.outcomeShort(),
                view.failureAnalysis(),
                view.actionableFix(),
                view.keyAchievements(),
                fromHealthCheck(view.healthCheck())
        );
    }

    private static HealthCheckResponse fromHealthCheck(RunSummaryView.HealthCheckView view) {
        if (view == null) {
            return new HealthCheckResponse(
                    new IssueStatsResponse(0, "N/A"),
                    new IssueStatsResponse(0, "N/A"),
                    "N/A",
                    "No data"
            );
        }
        return new HealthCheckResponse(
                fromIssueStats(view.networkIssues()),
                fromIssueStats(view.consoleIssues()),
                view.accessibilityScore(),
                view.accessibilitySummary()
        );
    }

    private static IssueStatsResponse fromIssueStats(RunSummaryView.IssueStatsView view) {
        if (view == null) {
            return new IssueStatsResponse(0, "N/A");
        }
        return new IssueStatsResponse(view.count(), view.summary());
    }
}
