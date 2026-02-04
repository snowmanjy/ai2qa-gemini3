package com.ai2qa.application.browser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ConsoleSpyService.
 */
@DisplayName("ConsoleSpyService")
class ConsoleSpyServiceTest {

    private ConsoleSpyService consoleSpyService;

    @BeforeEach
    void setUp() {
        consoleSpyService = new ConsoleSpyService();
    }

    @Nested
    @DisplayName("ErrorCollector")
    class ErrorCollectorTests {

        @Test
        @DisplayName("should capture console errors")
        void capturesConsoleErrors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When
            collector.recordConsoleError("Some JS error");

            // Then
            assertThat(collector.getErrors()).containsExactly("[JS Console] Some JS error");
        }

        @Test
        @DisplayName("should truncate long errors")
        void truncatesErrors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();
            String longError = "a".repeat(1000);

            // When
            collector.recordConsoleError(longError);

            // Then
            String captured = collector.getErrors().get(0);
            assertThat(captured).startsWith("[JS Console] ");
            assertThat(captured).endsWith("...");
            assertThat(captured.length()).isLessThan(600);
        }

        @Test
        @DisplayName("should capture page errors (uncaught exceptions)")
        void capturesPageErrors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When
            collector.recordPageError("Uncaught TypeError: Cannot read property 'x' of undefined");

            // Then
            assertThat(collector.getErrors()).containsExactly("[Uncaught Exception] Uncaught TypeError: Cannot read property 'x' of undefined");
        }

        @Test
        @DisplayName("should stop capturing when stopped")
        void stopsCapturing() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();
            collector.stop();

            // When
            collector.recordConsoleError("Error after stop");

            // Then
            assertThat(collector.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("should filter out AI thinking used as selectors")
        void filtersAiThinkingFromSelectors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When - AI thinking accidentally used as a selector
            collector.recordPageError("SyntaxError: Failed to execute 'querySelector' on 'Document': " +
                    "'I need to find a sign up button or create account link in the DOM snapshot provided." +
                    " Let me analyze the visible elements...'");

            // Then - Should be filtered out
            assertThat(collector.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("should filter out AI analysis patterns from page errors")
        void filtersAiAnalysisPatterns() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When - Various AI thinking patterns
            collector.recordPageError("Let me analyze the page structure");
            collector.recordPageError("Looking at the header section [s45-s48]");
            collector.recordPageError("I can see a login button");
            collector.recordPageError("The snapshot appears to be truncated");
            collector.recordPageError("Based on typical website patterns");

            // Then - All should be filtered out
            assertThat(collector.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("should keep legitimate JavaScript errors")
        void keepsLegitimateErrors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When - Real JS errors
            collector.recordPageError("TypeError: Cannot read property 'map' of undefined");
            collector.recordPageError("ReferenceError: someFunction is not defined");

            // Then - Should be kept
            assertThat(collector.getErrors()).hasSize(2);
        }

        @Test
        @DisplayName("should deduplicate repeated errors")
        void deduplicatesRepeatedErrors() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When - Same error multiple times
            collector.recordPageError("TypeError: Cannot read property 'x' of undefined");
            collector.recordPageError("TypeError: Cannot read property 'x' of undefined");
            collector.recordPageError("TypeError: Cannot read property 'x' of undefined");

            // Then - Should only appear once
            assertThat(collector.getErrors()).hasSize(1);
        }

        @Test
        @DisplayName("should filter out third-party tracker noise")
        void filtersTrackerNoise() {
            // Given
            ConsoleSpyService.ErrorCollector collector = consoleSpyService.startCapturing();

            // When - Third-party tracker errors
            collector.recordConsoleError("Error loading googletag.js");
            collector.recordConsoleError("facebook pixel failed to load");
            collector.recordConsoleError("gtag not defined");

            // Then - Should be filtered out
            assertThat(collector.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("analyzeForHealer")
    class AnalyzeForHealerTests {

        @Test
        @DisplayName("should return empty string for empty errors")
        void emptyForNoErrors() {
            assertThat(consoleSpyService.analyzeForHealer(List.of())).isEmpty();
            assertThat(consoleSpyService.analyzeForHealer(null)).isEmpty();
        }

        @Test
        @DisplayName("should format errors for Healer")
        void formatsErrors() {
            List<String> errors = List.of(
                    "[JS Console] Error 1",
                    "[Uncaught Exception] Error 2"
            );

            String analysis = consoleSpyService.analyzeForHealer(errors);

            assertThat(analysis).contains("[JAVASCRIPT ERRORS DETECTED]");
            assertThat(analysis).contains("• [JS Console] Error 1");
            assertThat(analysis).contains("• [Uncaught Exception] Error 2");
        }

        @Test
        @DisplayName("should highlight hydration errors")
        void highlightsHydrationErrors() {
            List<String> errors = List.of("[JS Console] Hydration failed because the initial UI does not match");

            String analysis = consoleSpyService.analyzeForHealer(errors);

            assertThat(analysis).contains("⚠️ Hydration Error Detected");
            assertThat(analysis).contains("Next.js/React SSR mismatch");
        }

        @Test
        @DisplayName("should highlight null pointer exceptions")
        void highlightsNPE() {
            List<String> errors = List.of("[Uncaught Exception] Uncaught TypeError: Cannot read properties of undefined (reading 'map')");

            String analysis = consoleSpyService.analyzeForHealer(errors);

            assertThat(analysis).contains("⚠️ Null Pointer Exception");
            assertThat(analysis).contains("frontend code crashed");
        }
    }
}
