package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.ActionStepFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutedStep consoleErrors field.
 */
@DisplayName("ExecutedStep Console Errors")
class ExecutedStepConsoleErrorsTest {

    private static final Instant TEST_TIME = Instant.parse("2024-01-15T10:00:00Z");

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() should accept console errors")
        void successWithConsoleErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("submit button");
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> consoleErrors = List.of(
                    "[JS Console] TypeError: undefined",
                    "[Uncaught Exception] Crash"
            );

            // When
            ExecutedStep result = ExecutedStep.success(
                    step, "button#submit", snapshot, snapshot, 100, 0, null, null, null, consoleErrors, TEST_TIME
            );

            // Then
            assertThat(result.consoleErrors()).hasSize(2);
            assertThat(result.hasConsoleErrors()).isTrue();
            assertThat(result.consoleErrors().get(0)).contains("TypeError");
        }

        @Test
        @DisplayName("failed() should accept console errors")
        void failedWithConsoleErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("submit button");
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> consoleErrors = List.of("[JS Console] Hydration failed");

            // When
            ExecutedStep result = ExecutedStep.failed(
                    step, "Error", snapshot, 0, null, null, null, consoleErrors, TEST_TIME
            );

            // Then
            assertThat(result.isFailed()).isTrue();
            assertThat(result.hasConsoleErrors()).isTrue();
            assertThat(result.consoleErrors()).containsExactly("[JS Console] Hydration failed");
        }

        @Test
        @DisplayName("timeout() should accept console errors")
        void timeoutWithConsoleErrors() {
            // Given
            ActionStep step = ActionStepFactory.waitFor("spinner", 1000);
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> consoleErrors = List.of("[JS Console] Infinite loop detected");

            // When
            ExecutedStep result = ExecutedStep.timeout(step, snapshot, 5000, null, consoleErrors, TEST_TIME);

            // Then
            assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.TIMEOUT);
            assertThat(result.hasConsoleErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("Console Errors Summary")
    class ConsoleErrorsSummaryTests {

        @Test
        @DisplayName("should return empty string when no errors")
        void emptyWhenNoErrors() {
            ActionStep step = ActionStepFactory.click("button");
            ExecutedStep result = ExecutedStep.success(step, "button", null, null, 50, TEST_TIME);

            assertThat(result.consoleErrorsSummary()).isEmpty();
        }

        @Test
        @DisplayName("should join errors with newlines")
        void joinsWithNewlines() {
            ActionStep step = ActionStepFactory.click("button");
            List<String> errors = List.of("Error 1", "Error 2");
            
            ExecutedStep result = ExecutedStep.success(
                    step, "button", null, null, 50, 0, null, null, null, errors, TEST_TIME
            );

            String summary = result.consoleErrorsSummary();
            
            assertThat(summary).contains("Error 1");
            assertThat(summary).contains("Error 2");
            assertThat(summary.split("\n")).hasSize(2);
        }
    }
}
