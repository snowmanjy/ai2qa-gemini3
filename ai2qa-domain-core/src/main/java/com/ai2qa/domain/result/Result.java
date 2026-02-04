package com.ai2qa.domain.result;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A Result type that represents either a success or a failure.
 *
 * <p>
 * Used instead of throwing exceptions for expected failures.
 * Follows functional programming principles per CLAUDE.md.
 *
 * @param <T> The type of the success value
 */
public sealed interface Result<T> {

    /**
     * Creates a successful result.
     */
    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a successful result with no value (for void operations).
     */
    static Result<Void> ok() {
        return new Success<>(null);
    }

    /**
     * Creates a failure result.
     */
    static <T> Result<T> failure(String reason) {
        return new Failure<>(reason);
    }

    /**
     * Creates a failure result with a cause.
     */
    static <T> Result<T> failure(String reason, Throwable cause) {
        return new Failure<>(reason + ": " + cause.getMessage());
    }

    /**
     * Checks if this result is a success.
     */
    boolean isSuccess();

    /**
     * Checks if this result is a failure.
     */
    boolean isFailure();

    /**
     * Returns the value if success, or empty if failure.
     */
    Optional<T> getValue();

    /**
     * Returns the failure reason if failure, or empty if success.
     */
    Optional<String> getFailureReason();

    /**
     * Maps the success value to a new type.
     */
    <U> Result<U> map(Function<T, U> mapper);

    /**
     * Flat maps the success value to a new Result.
     */
    <U> Result<U> flatMap(Function<T, Result<U>> mapper);

    /**
     * Executes an action if this is a success.
     */
    Result<T> onSuccess(Consumer<T> action);

    /**
     * Executes an action if this is a failure.
     */
    Result<T> onFailure(Consumer<String> action);

    /**
     * Returns the value or throws if failure.
     * Should only be used at boundaries where exceptions are acceptable.
     */
    T orElseThrow();

    /**
     * Success implementation.
     */
    record Success<T>(T value) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isFailure() {
            return false;
        }

        @Override
        public Optional<T> getValue() {
            return Optional.ofNullable(value);
        }

        @Override
        public Optional<String> getFailureReason() {
            return Optional.empty();
        }

        @Override
        public <U> Result<U> map(Function<T, U> mapper) {
            return new Success<>(mapper.apply(value));
        }

        @Override
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            return mapper.apply(value);
        }

        @Override
        public Result<T> onSuccess(Consumer<T> action) {
            action.accept(value);
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<String> action) {
            return this;
        }

        @Override
        public T orElseThrow() {
            return value;
        }
    }

    /**
     * Failure implementation.
     */
    record Failure<T>(String reason) implements Result<T> {

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isFailure() {
            return true;
        }

        @Override
        public Optional<T> getValue() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getFailureReason() {
            return Optional.of(reason);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> map(Function<T, U> mapper) {
            return (Result<U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
            return (Result<U>) this;
        }

        @Override
        public Result<T> onSuccess(Consumer<T> action) {
            return this;
        }

        @Override
        public Result<T> onFailure(Consumer<String> action) {
            action.accept(reason);
            return this;
        }

        @Override
        public T orElseThrow() {
            throw new IllegalStateException("Result is failure: " + reason);
        }
    }
}
