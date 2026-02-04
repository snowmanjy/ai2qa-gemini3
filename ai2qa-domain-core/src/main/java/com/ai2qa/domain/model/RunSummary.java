package com.ai2qa.domain.model;

import java.util.List;

/**
 * Structured summary for test run reports.
 *
 * <p>Uses a "Mad Libs" approach where the AI fills in specific fields
 * rather than generating free-form text. This ensures consistent,
 * enterprise-grade reports.
 *
 * <p>The frontend uses this to render branded reports with:
 * - Green theme for SUCCESS
 * - Red theme with diagnostic sections for FAILURE
 */
public record RunSummary(
        /**
         * The overall status: "SUCCESS" or "FAILURE"
         */
        String status,

        /**
         * Brief overview of what the user wanted to test.
         * Example: "User wanted to login and verify shopping cart contents."
         */
        String goalOverview,

        /**
         * One-line outcome description.
         * Example (Success): "Login successful, 3 items verified in cart."
         * Example (Failure): "Login succeeded but cart page failed to load."
         */
        String outcomeShort,

        /**
         * Technical explanation of why the test failed.
         * Null if status is SUCCESS.
         * Example: "Selector '#add-to-cart-btn' not found after 5s timeout."
         */
        String failureAnalysis,

        /**
         * Specific code change or data fix needed.
         * Null if status is SUCCESS.
         * Example: "Update selector to '[data-testid=\"cart-button\"]'"
         */
        String actionableFix,

        /**
         * Top 3-5 major actions completed before completion/failure.
         * Example: ["Navigated to homepage", "Logged in as test@example.com", "Added item to cart"]
         */
        /**
         * Top 3-5 major actions completed before completion/failure.
         * Example: ["Navigated to homepage", "Logged in as test@example.com", "Added item to cart"]
         */
        List<String> keyAchievements,

        /**
         * Report health diagnostics.
         * Contains stats on network errors, console logs, and accessibility.
         */
        HealthCheck healthCheck
) {
    /**
     * Diagnostic health check stats.
     */
    public record HealthCheck(
            IssueStats networkIssues,
            IssueStats consoleIssues,
            String accessibilityScore, // "A", "B", "C"
            String accessibilitySummary
    ) {}

    /**
     * Stats for a specific issue type.
     */
    public record IssueStats(
            int count,
            String summary
    ) {}

    /**
     * Creates a success summary.
     */
    public static RunSummary success(
            String goalOverview,
            String outcomeShort,
            List<String> keyAchievements,
            HealthCheck healthCheck) {
        return new RunSummary(
                "SUCCESS",
                goalOverview,
                outcomeShort,
                null,
                null,
                keyAchievements != null ? List.copyOf(keyAchievements) : List.of(),
                healthCheck
        );
    }

    /**
     * Creates a failure summary with diagnostic information.
     */
    public static RunSummary failure(
            String goalOverview,
            String outcomeShort,
            String failureAnalysis,
            String actionableFix,
            List<String> keyAchievements,
            HealthCheck healthCheck) {
        return new RunSummary(
                "FAILURE",
                goalOverview,
                outcomeShort,
                failureAnalysis,
                actionableFix,
                keyAchievements != null ? List.copyOf(keyAchievements) : List.of(),
                healthCheck
        );
    }

    /**
     * Returns true if this is a successful run.
     */
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }
}
