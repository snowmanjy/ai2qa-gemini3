package com.ai2qa.application.run.view;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record ExecutedStepView(
        ActionStepView step,
        String status,
        Instant executedAt,
        long durationMs,
        Optional<String> selectorUsed,
        Optional<DomSnapshotView> snapshotBefore,
        Optional<DomSnapshotView> snapshotAfter,
        Optional<String> errorMessage,
        int retryCount,
        Optional<String> optimizationSuggestion,
        List<String> networkErrors,
        List<String> accessibilityWarnings,
        List<String> consoleErrors,
        Optional<PerformanceMetricsView> performanceMetrics,
        boolean hasScreenshot
) {
    public ExecutedStepView {
        if (selectorUsed == null) {
            selectorUsed = Optional.empty();
        }
        if (snapshotBefore == null) {
            snapshotBefore = Optional.empty();
        }
        if (snapshotAfter == null) {
            snapshotAfter = Optional.empty();
        }
        if (errorMessage == null) {
            errorMessage = Optional.empty();
        }
        if (optimizationSuggestion == null) {
            optimizationSuggestion = Optional.empty();
        }
        if (networkErrors == null) {
            networkErrors = List.of();
        }
        if (accessibilityWarnings == null) {
            accessibilityWarnings = List.of();
        }
        if (consoleErrors == null) {
            consoleErrors = List.of();
        }
        if (performanceMetrics == null) {
            performanceMetrics = Optional.empty();
        }
    }
}
