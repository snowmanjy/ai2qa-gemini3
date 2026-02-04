package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.ActionStepFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutedStep networkErrors field.
 */
@DisplayName("ExecutedStep Network Errors")
class ExecutedStepNetworkErrorsTest {

    private static final Instant TEST_TIME = Instant.parse("2024-01-15T10:00:00Z");

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("success() should default to empty network errors")
        void successDefaultsToEmptyNetworkErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("submit button");
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");

            // When
            ExecutedStep result = ExecutedStep.success(step, "button#submit", snapshot, snapshot, 100, TEST_TIME);

            // Then
            assertThat(result.networkErrors()).isEmpty();
            assertThat(result.hasNetworkErrors()).isFalse();
        }

        @Test
        @DisplayName("success() should accept network errors")
        void successWithNetworkErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("submit button");
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> errors = List.of(
                    "[500 Internal Server Error] POST /api/submit",
                    "[404 Not Found] GET /api/data"
            );

            // When
            ExecutedStep result = ExecutedStep.success(
                    step, "button#submit", snapshot, snapshot, 100, 0, null, errors, TEST_TIME
            );

            // Then
            assertThat(result.networkErrors()).hasSize(2);
            assertThat(result.hasNetworkErrors()).isTrue();
            assertThat(result.networkErrors().get(0)).contains("500");
        }

        @Test
        @DisplayName("failed() should include network errors")
        void failedWithNetworkErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("submit button");
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> errors = List.of("[500 Internal Server Error] POST /api/login");

            // When
            ExecutedStep result = ExecutedStep.failed(
                    step, "Element not clickable", snapshot, 2, "Backend is down", errors, TEST_TIME
            );

            // Then
            assertThat(result.isFailed()).isTrue();
            assertThat(result.hasNetworkErrors()).isTrue();
            assertThat(result.networkErrors()).containsExactly("[500 Internal Server Error] POST /api/login");
        }

        @Test
        @DisplayName("timeout() should include network errors")
        void timeoutWithNetworkErrors() {
            // Given
            ActionStep step = ActionStepFactory.waitFor("loading spinner", 5000);
            DomSnapshot snapshot = DomSnapshot.of("<html></html>", "https://example.com", "Test");
            List<String> errors = List.of("[504 Gateway Timeout] GET /api/slow-endpoint");

            // When
            ExecutedStep result = ExecutedStep.timeout(step, snapshot, 30000, errors, TEST_TIME);

            // Then
            assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.TIMEOUT);
            assertThat(result.hasNetworkErrors()).isTrue();
            assertThat(result.networkErrors().get(0)).contains("504");
        }

        @Test
        @DisplayName("skipped() should have empty network errors")
        void skippedHasNoNetworkErrors() {
            // Given
            ActionStep step = ActionStepFactory.click("optional button");

            // When
            ExecutedStep result = ExecutedStep.skipped(step, "Previous step failed", TEST_TIME);

            // Then
            assertThat(result.networkErrors()).isEmpty();
            assertThat(result.hasNetworkErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("Network Errors Summary")
    class NetworkErrorsSummaryTests {

        @Test
        @DisplayName("should return empty string when no errors")
        void emptyWhenNoErrors() {
            ActionStep step = ActionStepFactory.click("button");
            ExecutedStep result = ExecutedStep.success(step, "button", null, null, 50, TEST_TIME);

            assertThat(result.networkErrorsSummary()).isEmpty();
        }

        @Test
        @DisplayName("should join errors with newlines")
        void joinsWithNewlines() {
            ActionStep step = ActionStepFactory.click("button");
            List<String> errors = List.of("Error 1", "Error 2", "Error 3");

            ExecutedStep result = ExecutedStep.success(
                    step, "button", null, null, 50, 0, null, errors, TEST_TIME
            );

            String summary = result.networkErrorsSummary();
            
            assertThat(summary).contains("Error 1");
            assertThat(summary).contains("Error 2");
            assertThat(summary).contains("Error 3");
            assertThat(summary.split("\n")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("should create defensive copy of network errors list")
        void defensiveCopy() {
            // Given
            ActionStep step = ActionStepFactory.click("button");
            List<String> originalErrors = new ArrayList<>();
            originalErrors.add("Error 1");

            // When
            ExecutedStep result = ExecutedStep.success(
                    step, "button", null, null, 50, 0, null, originalErrors, TEST_TIME
            );

            // Modify original list
            originalErrors.add("Error 2");

            // Then - ExecutedStep should not be affected
            assertThat(result.networkErrors()).hasSize(1);
            assertThat(result.networkErrors()).containsExactly("Error 1");
        }
    }
}
