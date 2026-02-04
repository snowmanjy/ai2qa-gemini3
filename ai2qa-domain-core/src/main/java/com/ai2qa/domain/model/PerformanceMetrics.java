package com.ai2qa.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Performance metrics captured from Core Web Vitals measurement.
 *
 * <p>Stores web performance data from the get_performance_metrics tool,
 * including Core Web Vitals (LCP, CLS, FID, FCP, TTFB) and navigation timing.
 *
 * @param webVitals Core Web Vitals metrics (LCP, CLS, FID, FCP, TTFB in ms)
 * @param navigation Navigation timing breakdown (DNS, TCP, TLS, TTFB, pageLoad in ms)
 * @param totalResources Number of resources loaded
 * @param totalTransferSizeKb Total transfer size in KB
 * @param issues List of performance issues detected with severity
 */
public record PerformanceMetrics(
        Map<String, Double> webVitals,
        Map<String, Double> navigation,
        Integer totalResources,
        Integer totalTransferSizeKb,
        List<PerformanceIssue> issues
) {

    /**
     * Compact constructor for backward compatibility.
     */
    public PerformanceMetrics {
        webVitals = webVitals != null ? Map.copyOf(webVitals) : Map.of();
        navigation = navigation != null ? Map.copyOf(navigation) : Map.of();
        issues = issues != null ? List.copyOf(issues) : List.of();
    }

    /**
     * A single performance issue detected.
     */
    public record PerformanceIssue(
            String severity,
            String category,
            String message,
            Double value,
            Double threshold
    ) {}

    /**
     * Returns true if any metrics were captured.
     */
    public boolean hasMetrics() {
        return !webVitals.isEmpty() || !navigation.isEmpty();
    }

    /**
     * Returns LCP (Largest Contentful Paint) in milliseconds if available.
     */
    public Optional<Double> getLcp() {
        return Optional.ofNullable(webVitals.get("lcp"));
    }

    /**
     * Returns CLS (Cumulative Layout Shift) score if available.
     */
    public Optional<Double> getCls() {
        return Optional.ofNullable(webVitals.get("cls"));
    }

    /**
     * Returns FID (First Input Delay) in milliseconds if available.
     */
    public Optional<Double> getFid() {
        return Optional.ofNullable(webVitals.get("fid"));
    }

    /**
     * Returns FCP (First Contentful Paint) in milliseconds if available.
     */
    public Optional<Double> getFcp() {
        return Optional.ofNullable(webVitals.get("fcp"));
    }

    /**
     * Returns TTFB (Time to First Byte) in milliseconds if available.
     */
    public Optional<Double> getTtfb() {
        return Optional.ofNullable(webVitals.get("ttfb"));
    }

    /**
     * Returns page load time in milliseconds if available.
     */
    public Optional<Double> getPageLoad() {
        return Optional.ofNullable(navigation.get("pageLoad"));
    }

    /**
     * Returns DOM content loaded time in milliseconds if available.
     */
    public Optional<Double> getDomContentLoaded() {
        return Optional.ofNullable(navigation.get("domContentLoaded"));
    }

    /**
     * Returns true if there are any critical or high severity issues.
     */
    public boolean hasCriticalIssues() {
        return issues.stream()
                .anyMatch(i -> "CRITICAL".equals(i.severity()) || "HIGH".equals(i.severity()));
    }

    /**
     * Returns a summary string for display.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        getLcp().ifPresent(v -> sb.append("LCP: ").append(formatMs(v)).append(" | "));
        getCls().ifPresent(v -> sb.append("CLS: ").append(String.format("%.3f", v)).append(" | "));
        getTtfb().ifPresent(v -> sb.append("TTFB: ").append(formatMs(v)).append(" | "));
        getPageLoad().ifPresent(v -> sb.append("Load: ").append(formatMs(v)));
        return sb.toString().replaceAll(" \\| $", "");
    }

    private String formatMs(double ms) {
        if (ms >= 1000) {
            return String.format("%.2fs", ms / 1000);
        }
        return String.format("%.0fms", ms);
    }

    /**
     * Creates an empty metrics instance.
     */
    public static PerformanceMetrics empty() {
        return new PerformanceMetrics(Map.of(), Map.of(), null, null, List.of());
    }
}
