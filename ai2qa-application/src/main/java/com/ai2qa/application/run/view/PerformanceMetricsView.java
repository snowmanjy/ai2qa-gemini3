package com.ai2qa.application.run.view;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * View model for performance metrics captured from measure_performance steps.
 */
public record PerformanceMetricsView(
        Map<String, Double> webVitals,
        Map<String, Double> navigation,
        Optional<Integer> totalResources,
        Optional<Integer> totalTransferSizeKb,
        List<PerformanceIssueView> issues,
        String summary
) {
    public PerformanceMetricsView {
        if (webVitals == null) {
            webVitals = Map.of();
        }
        if (navigation == null) {
            navigation = Map.of();
        }
        if (totalResources == null) {
            totalResources = Optional.empty();
        }
        if (totalTransferSizeKb == null) {
            totalTransferSizeKb = Optional.empty();
        }
        if (issues == null) {
            issues = List.of();
        }
    }

    /**
     * View model for a single performance issue.
     */
    public record PerformanceIssueView(
            String severity,
            String category,
            String message,
            Optional<Double> value,
            Optional<Double> threshold
    ) {
        public PerformanceIssueView {
            if (value == null) {
                value = Optional.empty();
            }
            if (threshold == null) {
                threshold = Optional.empty();
            }
        }
    }
}
