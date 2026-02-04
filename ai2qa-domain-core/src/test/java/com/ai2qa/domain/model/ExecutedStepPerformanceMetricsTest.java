package com.ai2qa.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ExecutedStep performance metrics integration.
 * Verifies that performance metrics from measure_performance steps are properly stored and accessible.
 */
class ExecutedStepPerformanceMetricsTest {

    private static final Instant NOW = Instant.parse("2026-01-30T10:00:00Z");

    @Nested
    @DisplayName("Performance metrics storage")
    class PerformanceMetricsStorage {

        @Test
        @DisplayName("Should store performance metrics in successful step")
        void successWithPerformanceMetrics() {
            ActionStep step = createMeasurePerformanceStep();
            PerformanceMetrics metrics = createSampleMetrics();

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), metrics, NOW
            );

            assertThat(executed.hasPerformanceMetrics()).isTrue();
            assertThat(executed.performanceMetrics()).isEqualTo(metrics);
            assertThat(executed.performanceMetricsOpt()).isPresent();
            assertThat(executed.performanceMetricsOpt().get().getLcp()).contains(2500.0);
        }

        @Test
        @DisplayName("Should handle null performance metrics for non-performance steps")
        void nonPerformanceStepNoMetrics() {
            ActionStep step = new ActionStep("step-1", "click", "button", Optional.empty(), Optional.empty(), Map.of());

            ExecutedStep executed = ExecutedStep.success(
                    step, "button[type=submit]", null, null, 500, 0, null,
                    List.of(), List.of(), List.of(), null, NOW
            );

            assertThat(executed.hasPerformanceMetrics()).isFalse();
            assertThat(executed.performanceMetrics()).isNull();
            assertThat(executed.performanceMetricsOpt()).isEmpty();
        }

        @Test
        @DisplayName("Should return empty summary for steps without metrics")
        void emptySummaryWhenNoMetrics() {
            ActionStep step = new ActionStep("step-1", "navigate", "https://example.com", Optional.empty(), Optional.empty(), Map.of());

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), null, NOW
            );

            assertThat(executed.performanceMetricsSummary()).isEmpty();
        }

        @Test
        @DisplayName("Should return metrics summary for performance step")
        void summaryWhenMetricsPresent() {
            ActionStep step = createMeasurePerformanceStep();
            PerformanceMetrics metrics = createSampleMetrics();

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), metrics, NOW
            );

            String summary = executed.performanceMetricsSummary();
            assertThat(summary).contains("LCP:");
            assertThat(summary).contains("CLS:");
        }
    }

    @Nested
    @DisplayName("Backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("Factory methods without performance metrics should set null")
        void factoryMethodsWithoutMetrics() {
            ActionStep step = new ActionStep("step-1", "click", "button", Optional.empty(), Optional.empty(), Map.of());

            // Use the older factory method signature
            ExecutedStep executed = ExecutedStep.success(
                    step, "button", null, null, 500, NOW
            );

            assertThat(executed.hasPerformanceMetrics()).isFalse();
            assertThat(executed.performanceMetrics()).isNull();
        }

        @Test
        @DisplayName("Failed steps should not have performance metrics")
        void failedStepsNoMetrics() {
            ActionStep step = createMeasurePerformanceStep();

            ExecutedStep executed = ExecutedStep.failed(
                    step, "Tool execution failed", null, 0, NOW
            );

            assertThat(executed.hasPerformanceMetrics()).isFalse();
            assertThat(executed.performanceMetrics()).isNull();
        }

        @Test
        @DisplayName("Timeout steps should not have performance metrics")
        void timeoutStepsNoMetrics() {
            ActionStep step = createMeasurePerformanceStep();

            ExecutedStep executed = ExecutedStep.timeout(step, null, 30000, NOW);

            assertThat(executed.hasPerformanceMetrics()).isFalse();
        }

        @Test
        @DisplayName("Skipped steps should not have performance metrics")
        void skippedStepsNoMetrics() {
            ActionStep step = createMeasurePerformanceStep();

            ExecutedStep executed = ExecutedStep.skipped(step, "Skipped due to error", NOW);

            assertThat(executed.hasPerformanceMetrics()).isFalse();
        }
    }

    @Nested
    @DisplayName("Performance metrics access")
    class PerformanceMetricsAccess {

        @Test
        @DisplayName("Should access web vitals from executed step")
        void accessWebVitals() {
            ActionStep step = createMeasurePerformanceStep();
            PerformanceMetrics metrics = createSampleMetrics();

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), metrics, NOW
            );

            assertThat(executed.performanceMetricsOpt())
                    .hasValueSatisfying(m -> {
                        assertThat(m.getLcp()).contains(2500.0);
                        assertThat(m.getCls()).contains(0.05);
                        assertThat(m.getTtfb()).contains(800.0);
                        assertThat(m.getFcp()).contains(1500.0);
                    });
        }

        @Test
        @DisplayName("Should access navigation timing from executed step")
        void accessNavigationTiming() {
            ActionStep step = createMeasurePerformanceStep();
            PerformanceMetrics metrics = new PerformanceMetrics(
                    Map.of(),
                    Map.of("pageLoad", 3500.0, "domContentLoaded", 2000.0),
                    100, 500, List.of()
            );

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), metrics, NOW
            );

            assertThat(executed.performanceMetricsOpt())
                    .hasValueSatisfying(m -> {
                        assertThat(m.getPageLoad()).contains(3500.0);
                        assertThat(m.getDomContentLoaded()).contains(2000.0);
                        assertThat(m.totalResources()).isEqualTo(100);
                        assertThat(m.totalTransferSizeKb()).isEqualTo(500);
                    });
        }

        @Test
        @DisplayName("Should access performance issues from executed step")
        void accessPerformanceIssues() {
            ActionStep step = createMeasurePerformanceStep();
            List<PerformanceMetrics.PerformanceIssue> issues = List.of(
                    new PerformanceMetrics.PerformanceIssue("CRITICAL", "LCP", "LCP is 4.5s", 4500.0, 4000.0),
                    new PerformanceMetrics.PerformanceIssue("HIGH", "CLS", "CLS needs improvement", 0.15, 0.1)
            );
            PerformanceMetrics metrics = new PerformanceMetrics(
                    Map.of("lcp", 4500.0, "cls", 0.15),
                    Map.of(),
                    null, null, issues
            );

            ExecutedStep executed = ExecutedStep.success(
                    step, null, null, null, 1000, 0, null,
                    List.of(), List.of(), List.of(), metrics, NOW
            );

            assertThat(executed.performanceMetricsOpt())
                    .hasValueSatisfying(m -> {
                        assertThat(m.hasCriticalIssues()).isTrue();
                        assertThat(m.issues()).hasSize(2);
                        assertThat(m.issues().get(0).severity()).isEqualTo("CRITICAL");
                        assertThat(m.issues().get(0).category()).isEqualTo("LCP");
                    });
        }
    }

    private ActionStep createMeasurePerformanceStep() {
        return new ActionStep(
                "step-perf-1",
                "measure_performance",
                "initial page load metrics",
                Optional.empty(),
                Optional.empty(),
                Map.of("includeResources", "true")
        );
    }

    private PerformanceMetrics createSampleMetrics() {
        Map<String, Double> vitals = Map.of(
                "lcp", 2500.0,
                "cls", 0.05,
                "ttfb", 800.0,
                "fcp", 1500.0
        );
        Map<String, Double> navigation = Map.of(
                "pageLoad", 3500.0,
                "domContentLoaded", 2000.0
        );
        return new PerformanceMetrics(vitals, navigation, 100, 500, List.of());
    }
}
