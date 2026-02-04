package com.ai2qa.domain.result;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Result sealed interface.
 */
class ResultTest {

    @Nested
    class SuccessResult {

        @Test
        void shouldCreateSuccessWithValue() {
            // When
            Result<String> result = Result.success("value");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isFailure()).isFalse();
            assertThat(result.getValue()).contains("value");
            assertThat(result.getFailureReason()).isEmpty();
        }

        @Test
        void shouldCreateOkForVoidOperations() {
            // When
            Result<Void> result = Result.ok();

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getValue()).isEmpty(); // null value
        }

        @Test
        void shouldMapSuccessValue() {
            // Given
            Result<String> result = Result.success("hello");

            // When
            Result<Integer> mapped = result.map(String::length);

            // Then
            assertThat(mapped.isSuccess()).isTrue();
            assertThat(mapped.getValue()).contains(5);
        }

        @Test
        void shouldFlatMapSuccessValue() {
            // Given
            Result<String> result = Result.success("42");

            // When
            Result<Integer> flatMapped = result.flatMap(s -> Result.success(Integer.parseInt(s)));

            // Then
            assertThat(flatMapped.isSuccess()).isTrue();
            assertThat(flatMapped.getValue()).contains(42);
        }

        @Test
        void shouldExecuteOnSuccessAction() {
            // Given
            Result<String> result = Result.success("value");
            AtomicReference<String> captured = new AtomicReference<>();

            // When
            result.onSuccess(captured::set);

            // Then
            assertThat(captured.get()).isEqualTo("value");
        }

        @Test
        void shouldNotExecuteOnFailureActionForSuccess() {
            // Given
            Result<String> result = Result.success("value");
            AtomicBoolean executed = new AtomicBoolean(false);

            // When
            result.onFailure(reason -> executed.set(true));

            // Then
            assertThat(executed.get()).isFalse();
        }

        @Test
        void shouldReturnValueWithOrElseThrow() {
            // Given
            Result<String> result = Result.success("value");

            // When
            String value = result.orElseThrow();

            // Then
            assertThat(value).isEqualTo("value");
        }
    }

    @Nested
    class FailureResult {

        @Test
        void shouldCreateFailureWithReason() {
            // When
            Result<String> result = Result.failure("Something went wrong");

            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getFailureReason()).contains("Something went wrong");
            assertThat(result.getValue()).isEmpty();
        }

        @Test
        void shouldCreateFailureWithCause() {
            // When
            Result<String> result = Result.failure("Operation failed", new RuntimeException("Root cause"));

            // Then
            assertThat(result.getFailureReason()).isPresent();
            assertThat(result.getFailureReason().get()).contains("Operation failed");
            assertThat(result.getFailureReason().get()).contains("Root cause");
        }

        @Test
        void shouldNotMapFailure() {
            // Given
            Result<String> result = Result.failure("error");

            // When
            Result<Integer> mapped = result.map(String::length);

            // Then
            assertThat(mapped.isFailure()).isTrue();
            assertThat(mapped.getFailureReason()).contains("error");
        }

        @Test
        void shouldNotFlatMapFailure() {
            // Given
            Result<String> result = Result.failure("error");

            // When
            Result<Integer> flatMapped = result.flatMap(s -> Result.success(Integer.parseInt(s)));

            // Then
            assertThat(flatMapped.isFailure()).isTrue();
        }

        @Test
        void shouldExecuteOnFailureAction() {
            // Given
            Result<String> result = Result.failure("error");
            AtomicReference<String> captured = new AtomicReference<>();

            // When
            result.onFailure(captured::set);

            // Then
            assertThat(captured.get()).isEqualTo("error");
        }

        @Test
        void shouldNotExecuteOnSuccessActionForFailure() {
            // Given
            Result<String> result = Result.failure("error");
            AtomicBoolean executed = new AtomicBoolean(false);

            // When
            result.onSuccess(value -> executed.set(true));

            // Then
            assertThat(executed.get()).isFalse();
        }

        @Test
        void shouldThrowOnOrElseThrowForFailure() {
            // Given
            Result<String> result = Result.failure("error message");

            // Then
            assertThatThrownBy(result::orElseThrow)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("error message");
        }
    }

    @Nested
    class Chaining {

        @Test
        void shouldChainMultipleMaps() {
            // Given
            Result<Integer> result = Result.success(5);

            // When
            Result<String> chained = result
                    .map(n -> n * 2)
                    .map(n -> n + 1)
                    .map(Object::toString);

            // Then
            assertThat(chained.getValue()).contains("11");
        }

        @Test
        void shouldChainWithFlatMap() {
            // Given
            Result<Integer> result = Result.success(10);

            // When
            Result<String> chained = result
                    .flatMap(n -> n > 0 ? Result.success(n * 2) : Result.failure("Must be positive"))
                    .map(Object::toString);

            // Then
            assertThat(chained.getValue()).contains("20");
        }

        @Test
        void shouldShortCircuitOnFailure() {
            // Given
            Result<Integer> result = Result.success(-5);

            // When
            Result<String> chained = result
                    .flatMap(n -> n > 0 ? Result.success(n * 2) : Result.failure("Must be positive"))
                    .map(Object::toString);

            // Then
            assertThat(chained.isFailure()).isTrue();
            assertThat(chained.getFailureReason()).contains("Must be positive");
        }

        @Test
        void shouldChainOnSuccessAndOnFailure() {
            // Given
            Result<String> success = Result.success("value");
            Result<String> failure = Result.failure("error");

            AtomicBoolean successActionExecuted = new AtomicBoolean(false);
            AtomicBoolean failureActionExecuted = new AtomicBoolean(false);

            // When
            success
                    .onSuccess(v -> successActionExecuted.set(true))
                    .onFailure(r -> failureActionExecuted.set(true));

            failure
                    .onSuccess(v -> successActionExecuted.set(false))
                    .onFailure(r -> failureActionExecuted.set(true));

            // Then
            assertThat(successActionExecuted.get()).isTrue();
            assertThat(failureActionExecuted.get()).isTrue();
        }
    }

    @Nested
    class PatternMatching {

        @Test
        void shouldAllowPatternMatchingOnSuccess() {
            // Given
            Result<String> result = Result.success("value");

            // When
            String message = switch (result) {
                case Result.Success<String> s -> "Success: " + s.value();
                case Result.Failure<String> f -> "Failed: " + f.reason();
            };

            // Then
            assertThat(message).isEqualTo("Success: value");
        }

        @Test
        void shouldAllowPatternMatchingOnFailure() {
            // Given
            Result<String> result = Result.failure("error");

            // When
            String message = switch (result) {
                case Result.Success<String> s -> "Success: " + s.value();
                case Result.Failure<String> f -> "Failed: " + f.reason();
            };

            // Then
            assertThat(message).isEqualTo("Failed: error");
        }
    }
}
