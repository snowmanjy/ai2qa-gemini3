package com.ai2qa.domain.model;

/**
 * Categorizes test run failures for billing decisions.
 *
 * <p>Determines whether a failed test run should be charged to the user.
 * Users are only charged when they receive value from the test execution.
 */
public enum FailureCategory {

    /**
     * Test completed successfully - user received value.
     */
    COMPLETED,

    /**
     * Test ran but found issues - user received value (discovered bugs).
     */
    TEST_FAILED,

    /**
     * Blocked by internal security (TargetGuard, prompt injection).
     * Not user's fault - do not charge.
     */
    BLOCKED_INTERNAL,

    /**
     * Blocked by external factors (browser/MCP blocking, cloud shield).
     * Not user's fault - do not charge.
     */
    BLOCKED_EXTERNAL,

    /**
     * Internal system error (crashes, exceptions, bugs).
     * Not user's fault - do not charge.
     */
    SYSTEM_ERROR,

    /**
     * User cancelled the run.
     * User chose not to proceed - do not charge.
     */
    CANCELLED,

    /**
     * Run timed out before completion.
     * Infrastructure issue - do not charge.
     */
    TIMEOUT;

    /**
     * Determines if this failure category should result in credit deduction.
     *
     * <p>Only chargeable when the user received value from the test execution:
     * - COMPLETED: Test ran successfully
     * - TEST_FAILED: Test ran and found issues (still valuable)
     *
     * @return true if credit should be deducted, false otherwise
     */
    public boolean isChargeable() {
        return this == COMPLETED || this == TEST_FAILED;
    }
}
