package com.ai2qa.application.orchestrator;

import com.ai2qa.domain.model.ActionStep;

import java.util.List;
import java.util.Optional;

/**
 * Result of reflecting on an action execution.
 *
 * <p>Determines what the orchestrator should do next.
 */
public sealed interface ReflectionResult {

    /**
     * Action succeeded, continue to next step.
     */
    record Success(String selectorUsed) implements ReflectionResult {}

    /**
     * Action failed, retry with repair steps.
     */
    record Retry(
            String reason,
            List<ActionStep> repairSteps
    ) implements ReflectionResult {}

    /**
     * Wait for a condition before continuing.
     */
    record Wait(
            String reason,
            int waitMs
    ) implements ReflectionResult {}

    /**
     * Abort the test run.
     */
    record Abort(String reason) implements ReflectionResult {}

    /**
     * Skip this step and continue to next step.
     * Used for optional steps (like cookie consent) that don't affect test validity.
     */
    record Skip(String reason) implements ReflectionResult {}

    // ==================== Factory Methods ====================

    /**
     * Creates a success result.
     */
    static ReflectionResult success(String selectorUsed) {
        return new Success(selectorUsed);
    }

    /**
     * Creates a retry result with repair steps.
     */
    static ReflectionResult retry(String reason, List<ActionStep> repairSteps) {
        return new Retry(reason, repairSteps);
    }

    /**
     * Creates a retry result with a single repair step.
     */
    static ReflectionResult retry(String reason, ActionStep repairStep) {
        return new Retry(reason, List.of(repairStep));
    }

    /**
     * Creates a wait result.
     */
    static ReflectionResult waitFor(String reason, int waitMs) {
        return new Wait(reason, waitMs);
    }

    /**
     * Creates an abort result.
     */
    static ReflectionResult abort(String reason) {
        return new Abort(reason);
    }

    /**
     * Creates a skip result for optional steps.
     */
    static ReflectionResult skip(String reason) {
        return new Skip(reason);
    }

    // ==================== Query Methods ====================

    /**
     * Checks if this is a success result.
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }

    /**
     * Checks if this should retry.
     */
    default boolean isRetry() {
        return this instanceof Retry;
    }

    /**
     * Checks if this should wait.
     */
    default boolean isWait() {
        return this instanceof Wait;
    }

    /**
     * Checks if this should abort.
     */
    default boolean isAbort() {
        return this instanceof Abort;
    }

    /**
     * Checks if this step should be skipped.
     */
    default boolean isSkip() {
        return this instanceof Skip;
    }

    /**
     * Gets repair steps if this is a retry result.
     */
    default List<ActionStep> getRepairSteps() {
        if (this instanceof Retry retry) {
            return retry.repairSteps();
        }
        return List.of();
    }

    /**
     * Gets the reason/description for this result.
     */
    default String getReason() {
        return switch (this) {
            case Success s -> "Success with selector: " + s.selectorUsed();
            case Retry r -> r.reason();
            case Wait w -> w.reason();
            case Abort a -> a.reason();
            case Skip s -> s.reason();
        };
    }
}
