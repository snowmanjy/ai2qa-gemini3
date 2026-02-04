package com.ai2qa.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Represents a step that has been executed with its result.
 *
 * <p>Immutable record capturing execution outcome for the DoneQueue.
 *
 * @param step                    The original action step
 * @param status                  Execution status
 * @param executedAt              When the step was executed
 * @param durationMs              How long the step took
 * @param selectorUsed            The selector that was actually used
 * @param snapshotBefore          DOM snapshot before execution
 * @param snapshotAfter           DOM snapshot after execution
 * @param errorMessage            Error message if failed
 * @param retryCount              Number of retries attempted
 * @param optimizationSuggestion  AI-generated suggestion for fixing brittle code
 * @param networkErrors           HTTP 4xx/5xx errors captured during step execution
 * @param accessibilityWarnings   WCAG accessibility violations found during step (Phase 26)
 * @param consoleErrors           Browser console errors (JS exceptions, hydration errors) (Phase 27)
 * @param performanceMetrics      Core Web Vitals and navigation timing (for measure_performance steps)
 */
public record ExecutedStep(
        ActionStep step,
        ExecutionStatus status,
        Instant executedAt,
        long durationMs,
        String selectorUsed,
        DomSnapshot snapshotBefore,
        DomSnapshot snapshotAfter,
        String errorMessage,
        int retryCount,
        String optimizationSuggestion,
        List<String> networkErrors,
        List<String> accessibilityWarnings,
        List<String> consoleErrors,
        PerformanceMetrics performanceMetrics
) {

    /**
     * Compact constructor for backward compatibility with old JSON data.
     * Converts null lists to empty lists during deserialization.
     * This is NOT validation - it's normalization for Jackson deserialization
     * of records stored before these fields were added.
     */
    public ExecutedStep {
        networkErrors = networkErrors != null ? List.copyOf(networkErrors) : List.of();
        accessibilityWarnings = accessibilityWarnings != null ? List.copyOf(accessibilityWarnings) : List.of();
        consoleErrors = consoleErrors != null ? List.copyOf(consoleErrors) : List.of();
        // performanceMetrics can be null for non-performance steps
    }

    /**
     * Execution status enumeration.
     */
    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        SKIPPED,
        TIMEOUT
    }

    /**
     * Creates a successful execution result.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, 0, executedAt);
    }

    /**
     * Creates a successful execution result with retry metadata.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, retryCount, null, executedAt);
    }

    /**
     * Creates a successful execution result with optimization suggestion.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            String optimizationSuggestion,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, retryCount, optimizationSuggestion, List.of(), executedAt);
    }

    /**
     * Creates a successful execution result with network errors captured.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, retryCount, optimizationSuggestion, networkErrors, List.of(), executedAt);
    }

    /**
     * Creates a successful execution result with network errors and accessibility warnings.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            List<String> accessibilityWarnings,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, retryCount, optimizationSuggestion, networkErrors, accessibilityWarnings, List.of(), executedAt);
    }

    /**
     * Creates a successful execution result with network errors, a11y warnings, and console errors.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            List<String> accessibilityWarnings,
            List<String> consoleErrors,
            Instant executedAt
    ) {
        return success(step, selectorUsed, before, after, durationMs, retryCount, optimizationSuggestion,
                networkErrors, accessibilityWarnings, consoleErrors, null, executedAt);
    }

    /**
     * Creates a successful execution result with all fields including performance metrics.
     */
    public static ExecutedStep success(
            ActionStep step,
            String selectorUsed,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            List<String> accessibilityWarnings,
            List<String> consoleErrors,
            PerformanceMetrics performanceMetrics,
            Instant executedAt
    ) {
        return new ExecutedStep(
                step,
                ExecutionStatus.SUCCESS,
                executedAt,
                durationMs,
                selectorUsed,
                before,
                after,
                null,
                retryCount,
                optimizationSuggestion,
                networkErrors != null ? List.copyOf(networkErrors) : List.of(),
                accessibilityWarnings != null ? List.copyOf(accessibilityWarnings) : List.of(),
                consoleErrors != null ? List.copyOf(consoleErrors) : List.of(),
                performanceMetrics
        );
    }

    /**
     * Creates a failed execution result.
     */
    public static ExecutedStep failed(
            ActionStep step,
            String errorMessage,
            DomSnapshot before,
            int retryCount,
            Instant executedAt
    ) {
        return failed(step, errorMessage, before, retryCount, null, executedAt);
    }

    /**
     * Creates a failed execution result with optimization suggestion.
     */
    public static ExecutedStep failed(
            ActionStep step,
            String errorMessage,
            DomSnapshot before,
            int retryCount,
            String optimizationSuggestion,
            Instant executedAt
    ) {
        return failed(step, errorMessage, before, retryCount, optimizationSuggestion, List.of(), executedAt);
    }

    /**
     * Creates a failed execution result with network errors captured.
     */
    public static ExecutedStep failed(
            ActionStep step,
            String errorMessage,
            DomSnapshot before,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            Instant executedAt
    ) {
        return failed(step, errorMessage, before, retryCount, optimizationSuggestion, networkErrors, List.of(), executedAt);
    }

    /**
     * Creates a failed execution result with network errors and accessibility warnings.
     */
    public static ExecutedStep failed(
            ActionStep step,
            String errorMessage,
            DomSnapshot before,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            List<String> accessibilityWarnings,
            Instant executedAt
    ) {
        return failed(step, errorMessage, before, retryCount, optimizationSuggestion, networkErrors, accessibilityWarnings, List.of(), executedAt);
    }

    /**
     * Creates a failed execution result with all error types.
     */
    public static ExecutedStep failed(
            ActionStep step,
            String errorMessage,
            DomSnapshot before,
            int retryCount,
            String optimizationSuggestion,
            List<String> networkErrors,
            List<String> accessibilityWarnings,
            List<String> consoleErrors,
            Instant executedAt
    ) {
        return new ExecutedStep(
                step,
                ExecutionStatus.FAILED,
                executedAt,
                0,
                null,
                before,
                null,
                errorMessage,
                retryCount,
                optimizationSuggestion,
                networkErrors != null ? List.copyOf(networkErrors) : List.of(),
                accessibilityWarnings != null ? List.copyOf(accessibilityWarnings) : List.of(),
                consoleErrors != null ? List.copyOf(consoleErrors) : List.of(),
                null
        );
    }

    /**
     * Creates a timeout execution result.
     */
    public static ExecutedStep timeout(
            ActionStep step,
            DomSnapshot before,
            long durationMs,
            Instant executedAt
    ) {
        return timeout(step, before, durationMs, List.of(), executedAt);
    }

    /**
     * Creates a timeout execution result with network errors.
     */
    public static ExecutedStep timeout(
            ActionStep step,
            DomSnapshot before,
            long durationMs,
            List<String> networkErrors,
            Instant executedAt
    ) {
        return new ExecutedStep(
                step,
                ExecutionStatus.TIMEOUT,
                executedAt,
                durationMs,
                null,
                before,
                null,
                "Execution timed out",
                0,
                null,
                networkErrors != null ? List.copyOf(networkErrors) : List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    /**
     * Creates a timeout execution result with all errors.
     */
    public static ExecutedStep timeout(
            ActionStep step,
            DomSnapshot before,
            long durationMs,
            List<String> networkErrors,
            List<String> consoleErrors,
            Instant executedAt
    ) {
        return new ExecutedStep(
                step,
                ExecutionStatus.TIMEOUT,
                executedAt,
                durationMs,
                null,
                before,
                null,
                "Execution timed out",
                0,
                null,
                networkErrors != null ? List.copyOf(networkErrors) : List.of(),
                List.of(),
                consoleErrors != null ? List.copyOf(consoleErrors) : List.of(),
                null
        );
    }

    /**
     * Creates a skipped execution result.
     */
    public static ExecutedStep skipped(ActionStep step, String reason, Instant executedAt) {
        return skipped(step, reason, null, 0, executedAt);
    }

    /**
     * Creates a skipped execution result with snapshot and retry count.
     */
    public static ExecutedStep skipped(
            ActionStep step,
            String reason,
            DomSnapshot snapshotBefore,
            int retryCount,
            Instant executedAt
    ) {
        return new ExecutedStep(
                step,
                ExecutionStatus.SKIPPED,
                executedAt,
                0,
                null,
                snapshotBefore,
                null,
                reason,
                retryCount,
                null,
                List.of(),
                List.of(),
                List.of(),
                null
        );
    }

    /**
     * Checks if the execution was successful.
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * Checks if the execution failed.
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * Returns true if network errors were captured during this step.
     */
    public boolean hasNetworkErrors() {
        return networkErrors != null && !networkErrors.isEmpty();
    }

    /**
     * Returns true if accessibility warnings were found during this step.
     */
    public boolean hasAccessibilityWarnings() {
        return accessibilityWarnings != null && !accessibilityWarnings.isEmpty();
    }

    /**
     * Returns true if console errors were captured during this step.
     */
    public boolean hasConsoleErrors() {
        return consoleErrors != null && !consoleErrors.isEmpty();
    }

    /**
     * Returns the error message if present.
     */
    public Optional<String> errorMessageOpt() {
        return Optional.ofNullable(errorMessage).filter(s -> !s.isBlank());
    }

    /**
     * Returns the selector used if present.
     */
    public Optional<String> selectorUsedOpt() {
        return Optional.ofNullable(selectorUsed).filter(s -> !s.isBlank());
    }

    /**
     * Returns the optimization suggestion if present.
     */
    public Optional<String> optimizationSuggestionOpt() {
        return Optional.ofNullable(optimizationSuggestion).filter(s -> !s.isBlank());
    }

    /**
     * Returns formatted network errors for logging or display.
     */
    public String networkErrorsSummary() {
        if (!hasNetworkErrors()) return "";
        return String.join("\n", networkErrors);
    }

    /**
     * Returns formatted accessibility warnings for logging or display.
     */
    public String accessibilitySummary() {
        if (!hasAccessibilityWarnings()) return "";
        return String.join("\n", accessibilityWarnings);
    }

    /**
     * Returns formatted console errors for logging or display.
     */
    public String consoleErrorsSummary() {
        if (!hasConsoleErrors()) return "";
        return String.join("\n", consoleErrors);
    }

    /**
     * Convenience method to get the action type.
     */
    public String action() {
        return step != null ? step.action() : "unknown";
    }

    /**
     * Convenience method to get the target.
     */
    public String target() {
        return step != null ? step.target() : "unknown";
    }

    /**
     * Returns true if performance metrics were captured during this step.
     */
    public boolean hasPerformanceMetrics() {
        return performanceMetrics != null && performanceMetrics.hasMetrics();
    }

    /**
     * Returns the performance metrics if present.
     */
    public Optional<PerformanceMetrics> performanceMetricsOpt() {
        return Optional.ofNullable(performanceMetrics);
    }

    /**
     * Returns a summary of performance metrics for display.
     */
    public String performanceMetricsSummary() {
        if (!hasPerformanceMetrics()) return "";
        return performanceMetrics.summary();
    }
}
