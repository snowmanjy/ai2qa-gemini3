package com.ai2qa.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PerformanceMetrics domain model.
 * Verifies Core Web Vitals storage, threshold evaluation, and summary generation.
 */
class PerformanceMetricsTest {

    @Nested
    @DisplayName("Constructor and basic operations")
    class Construction {

        @Test
        @DisplayName("Should handle null fields gracefully")
        void nullFieldsNormalized() {
            PerformanceMetrics metrics = new PerformanceMetrics(null, null, null, null, null);

            assertThat(metrics.webVitals()).isEmpty();
            assertThat(metrics.navigation()).isEmpty();
            assertThat(metrics.issues()).isEmpty();
            assertThat(metrics.hasMetrics()).isFalse();
        }

        @Test
        @DisplayName("Should create metrics with web vitals")
        void withWebVitals() {
            Map<String, Double> vitals = Map.of(
                    "lcp", 2500.0,
                    "cls", 0.05,
                    "ttfb", 800.0,
                    "fcp", 1500.0
            );

            PerformanceMetrics metrics = new PerformanceMetrics(vitals, Map.of(), null, null, List.of());

            assertThat(metrics.hasMetrics()).isTrue();
            assertThat(metrics.getLcp()).contains(2500.0);
            assertThat(metrics.getCls()).contains(0.05);
            assertThat(metrics.getTtfb()).contains(800.0);
            assertThat(metrics.getFcp()).contains(1500.0);
        }

        @Test
        @DisplayName("Should create metrics with navigation timing")
        void withNavigationTiming() {
            Map<String, Double> navigation = Map.of(
                    "pageLoad", 3500.0,
                    "domContentLoaded", 2000.0,
                    "dnsLookup", 50.0
            );

            PerformanceMetrics metrics = new PerformanceMetrics(Map.of(), navigation, 150, 2500, List.of());

            assertThat(metrics.hasMetrics()).isTrue();
            assertThat(metrics.getPageLoad()).contains(3500.0);
            assertThat(metrics.getDomContentLoaded()).contains(2000.0);
            assertThat(metrics.totalResources()).isEqualTo(150);
            assertThat(metrics.totalTransferSizeKb()).isEqualTo(2500);
        }
    }

    @Nested
    @DisplayName("Getters for Core Web Vitals")
    class WebVitalGetters {

        @Test
        @DisplayName("Should return empty Optional for missing metrics")
        void missingMetricsReturnEmpty() {
            PerformanceMetrics metrics = PerformanceMetrics.empty();

            assertThat(metrics.getLcp()).isEmpty();
            assertThat(metrics.getCls()).isEmpty();
            assertThat(metrics.getFid()).isEmpty();
            assertThat(metrics.getFcp()).isEmpty();
            assertThat(metrics.getTtfb()).isEmpty();
            assertThat(metrics.getPageLoad()).isEmpty();
        }

        @Test
        @DisplayName("Should return FID when present")
        void fidReturned() {
            Map<String, Double> vitals = Map.of("fid", 150.0);
            PerformanceMetrics metrics = new PerformanceMetrics(vitals, Map.of(), null, null, List.of());

            assertThat(metrics.getFid()).contains(150.0);
        }
    }

    @Nested
    @DisplayName("Issue handling")
    class IssueHandling {

        @Test
        @DisplayName("Should detect critical issues")
        void criticalIssuesDetected() {
            List<PerformanceMetrics.PerformanceIssue> issues = List.of(
                    new PerformanceMetrics.PerformanceIssue("CRITICAL", "LCP", "LCP is 4.5s", 4500.0, 4000.0)
            );

            PerformanceMetrics metrics = new PerformanceMetrics(Map.of(), Map.of(), null, null, issues);

            assertThat(metrics.hasCriticalIssues()).isTrue();
        }

        @Test
        @DisplayName("Should detect high severity issues")
        void highIssuesDetected() {
            List<PerformanceMetrics.PerformanceIssue> issues = List.of(
                    new PerformanceMetrics.PerformanceIssue("HIGH", "CLS", "CLS needs improvement", 0.15, 0.1)
            );

            PerformanceMetrics metrics = new PerformanceMetrics(Map.of(), Map.of(), null, null, issues);

            assertThat(metrics.hasCriticalIssues()).isTrue();
        }

        @Test
        @DisplayName("Should not flag medium/low issues as critical")
        void mediumLowNotCritical() {
            List<PerformanceMetrics.PerformanceIssue> issues = List.of(
                    new PerformanceMetrics.PerformanceIssue("MEDIUM", "TTFB", "TTFB needs improvement", 900.0, 800.0),
                    new PerformanceMetrics.PerformanceIssue("LOW", "RESOURCE", "Large image", 200.0, 100.0)
            );

            PerformanceMetrics metrics = new PerformanceMetrics(Map.of(), Map.of(), null, null, issues);

            assertThat(metrics.hasCriticalIssues()).isFalse();
        }
    }

    @Nested
    @DisplayName("Summary generation")
    class SummaryGeneration {

        @Test
        @DisplayName("Should generate summary with LCP, CLS, TTFB, Load")
        void fullSummary() {
            Map<String, Double> vitals = Map.of(
                    "lcp", 2500.0,
                    "cls", 0.05,
                    "ttfb", 800.0
            );
            Map<String, Double> navigation = Map.of("pageLoad", 3500.0);

            PerformanceMetrics metrics = new PerformanceMetrics(vitals, navigation, null, null, List.of());
            String summary = metrics.summary();

            assertThat(summary).contains("LCP:");
            assertThat(summary).contains("CLS:");
            assertThat(summary).contains("TTFB:");
            assertThat(summary).contains("Load:");
        }

        @Test
        @DisplayName("Should format milliseconds correctly")
        void formatMilliseconds() {
            Map<String, Double> vitals = Map.of(
                    "lcp", 500.0,    // Should be "500ms"
                    "ttfb", 2500.0   // Should be "2.50s"
            );

            PerformanceMetrics metrics = new PerformanceMetrics(vitals, Map.of(), null, null, List.of());
            String summary = metrics.summary();

            assertThat(summary).contains("500ms");
            assertThat(summary).contains("2.50s");
        }

        @Test
        @DisplayName("Should format CLS as decimal")
        void formatClsDecimal() {
            Map<String, Double> vitals = Map.of("cls", 0.123);

            PerformanceMetrics metrics = new PerformanceMetrics(vitals, Map.of(), null, null, List.of());
            String summary = metrics.summary();

            assertThat(summary).contains("CLS: 0.123");
        }

        @Test
        @DisplayName("Empty metrics should return empty summary")
        void emptySummary() {
            PerformanceMetrics metrics = PerformanceMetrics.empty();

            assertThat(metrics.summary()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Static factory")
    class StaticFactory {

        @Test
        @DisplayName("empty() should return metrics with no data")
        void emptyFactory() {
            PerformanceMetrics metrics = PerformanceMetrics.empty();

            assertThat(metrics.hasMetrics()).isFalse();
            assertThat(metrics.webVitals()).isEmpty();
            assertThat(metrics.navigation()).isEmpty();
            assertThat(metrics.issues()).isEmpty();
        }
    }
}
