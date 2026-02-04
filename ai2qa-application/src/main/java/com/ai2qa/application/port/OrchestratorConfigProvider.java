package com.ai2qa.application.port;

/**
 * Provider interface for orchestrator configuration.
 *
 * <p>This interface allows the application layer to access orchestrator settings
 * without depending on the boot layer. Configuration values can be changed
 * via application.yml without recompiling.
 *
 * <p>Configuration prefix: {@code ai2qa.orchestrator}
 */
public interface OrchestratorConfigProvider {

    /**
     * Maximum number of retry attempts for a single action step.
     *
     * <p>After this many retries, the step will either be skipped (if optional)
     * or cause the test to abort (if required).
     *
     * <p>Default: 3 (total of 4 attempts including initial)
     *
     * @return Maximum retry count per step
     */
    int getMaxRetries();

    /**
     * Maximum number of attempts to clear blocking obstacles (modals, popups)
     * before a single step execution.
     *
     * <p>Default: 3
     *
     * @return Maximum obstacle clearing attempts per step
     */
    int getMaxObstacleClearAttempts();

    /**
     * Global safety limit for loop iterations.
     *
     * <p>If a test run exceeds this many iterations without completing,
     * it will be terminated to prevent infinite loops.
     *
     * <p>Default: 50 (allows a 12-step test with all retries)
     *
     * @return Maximum loop iterations before forced termination
     */
    int getMaxLoopIterations();

    /**
     * Overall test run timeout in minutes.
     *
     * <p>Prevents tests from hanging forever due to AI API timeouts,
     * Chrome hangs, or other blocking operations.
     *
     * <p>Default: 30 minutes
     *
     * @return Maximum test duration in minutes before forced termination
     */
    int getTestTimeoutMinutes();
}
