package com.ai2qa.domain.model;

/**
 * Status of a test run lifecycle.
 */
public enum TestRunStatus {

    /**
     * Test run created but not yet started.
     */
    PENDING,

    /**
     * Test run is currently planning steps.
     */
    PLANNING,

    /**
     * Test run is actively executing steps.
     */
    RUNNING,

    /**
     * Test run is paused (e.g., waiting for user input).
     */
    PAUSED,

    /**
     * Test run completed successfully.
     */
    COMPLETED,

    /**
     * Test run failed with errors.
     */
    FAILED,

    /**
     * Test run was cancelled by user.
     */
    CANCELLED,

    /**
     * Test run timed out.
     */
    TIMEOUT;

    /**
     * Checks if this is a terminal state.
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * Checks if this is an active state (can execute steps).
     */
    public boolean isActive() {
        return this == RUNNING || this == PLANNING;
    }

    /**
     * Checks if the run can be cancelled.
     */
    public boolean canCancel() {
        return !isTerminal();
    }

    /**
     * Checks if the run can be resumed.
     */
    public boolean canResume() {
        return this == PAUSED;
    }
}
