package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.ActionStepFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExecutedStep record.
 */
class ExecutedStepTest {

    private static final Instant TEST_TIME = Instant.parse("2024-01-15T10:00:00Z");

    @Test
    void shouldCreateSuccessfulExecution() {
        // Given
        ActionStep action = ActionStepFactory.click("button");
        DomSnapshot before = DomSnapshot.of("before", "http://test.com", "Test");
        DomSnapshot after = DomSnapshot.of("after", "http://test.com", "Test");

        // When
        ExecutedStep result = ExecutedStep.success(action, "button#submit", before, after, 100L, TEST_TIME);

        // Then
        assertThat(result.step()).isEqualTo(action);
        assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.SUCCESS);
        assertThat(result.selectorUsed()).isEqualTo("button#submit");
        assertThat(result.snapshotBefore()).isEqualTo(before);
        assertThat(result.snapshotAfter()).isEqualTo(after);
        assertThat(result.durationMs()).isEqualTo(100L);
        assertThat(result.errorMessage()).isNull();
        assertThat(result.retryCount()).isZero();
        assertThat(result.executedAt()).isEqualTo(TEST_TIME);
    }

    @Test
    void shouldCreateFailedExecution() {
        // Given
        ActionStep action = ActionStepFactory.click("button");
        DomSnapshot before = DomSnapshot.of("before", "http://test.com", "Test");

        // When
        ExecutedStep result = ExecutedStep.failed(action, "Element not found", before, 2, TEST_TIME);

        // Then
        assertThat(result.step()).isEqualTo(action);
        assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.FAILED);
        assertThat(result.errorMessage()).isEqualTo("Element not found");
        assertThat(result.snapshotBefore()).isEqualTo(before);
        assertThat(result.snapshotAfter()).isNull();
        assertThat(result.retryCount()).isEqualTo(2);
        assertThat(result.selectorUsed()).isNull();
    }

    @Test
    void shouldCreateTimeoutExecution() {
        // Given
        ActionStep action = ActionStepFactory.click("button");
        DomSnapshot before = DomSnapshot.of("before", "http://test.com", "Test");

        // When
        ExecutedStep result = ExecutedStep.timeout(action, before, 30000L, TEST_TIME);

        // Then
        assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.TIMEOUT);
        assertThat(result.durationMs()).isEqualTo(30000L);
        assertThat(result.errorMessage()).isEqualTo("Execution timed out");
    }

    @Test
    void shouldCreateSkippedExecution() {
        // Given
        ActionStep action = ActionStepFactory.click("button");

        // When
        ExecutedStep result = ExecutedStep.skipped(action, "Precondition not met", TEST_TIME);

        // Then
        assertThat(result.status()).isEqualTo(ExecutedStep.ExecutionStatus.SKIPPED);
        assertThat(result.errorMessage()).isEqualTo("Precondition not met");
        assertThat(result.snapshotBefore()).isNull();
        assertThat(result.snapshotAfter()).isNull();
    }

    @Test
    void shouldIdentifySuccessfulStep() {
        // Given
        ExecutedStep success = ExecutedStep.success(
                ActionStepFactory.click("btn"), "btn", DomSnapshot.empty(), DomSnapshot.empty(), 100, TEST_TIME);
        ExecutedStep failed = ExecutedStep.failed(
                ActionStepFactory.click("btn"), "error", DomSnapshot.empty(), 0, TEST_TIME);

        // Then
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.isFailed()).isFalse();
        assertThat(failed.isSuccess()).isFalse();
        assertThat(failed.isFailed()).isTrue();
    }

    @Test
    void shouldReturnErrorMessageOptionalWhenPresent() {
        // Given
        ExecutedStep step = ExecutedStep.failed(
                ActionStepFactory.click("btn"), "Element not found", DomSnapshot.empty(), 0, TEST_TIME);

        // When
        Optional<String> result = step.errorMessageOpt();

        // Then
        assertThat(result).contains("Element not found");
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoErrorMessage() {
        // Given
        ExecutedStep step = ExecutedStep.success(
                ActionStepFactory.click("btn"), "btn", DomSnapshot.empty(), DomSnapshot.empty(), 100, TEST_TIME);

        // When
        Optional<String> result = step.errorMessageOpt();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnSelectorUsedOptionalWhenPresent() {
        // Given
        ExecutedStep step = ExecutedStep.success(
                ActionStepFactory.click("btn"), "button#submit", DomSnapshot.empty(), DomSnapshot.empty(), 100, TEST_TIME);

        // When
        Optional<String> result = step.selectorUsedOpt();

        // Then
        assertThat(result).contains("button#submit");
    }

    @Test
    void shouldReturnEmptyOptionalWhenNoSelectorUsed() {
        // Given
        ExecutedStep step = ExecutedStep.failed(
                ActionStepFactory.click("btn"), "error", DomSnapshot.empty(), 0, TEST_TIME);

        // When
        Optional<String> result = step.selectorUsedOpt();

        // Then
        assertThat(result).isEmpty();
    }
}
