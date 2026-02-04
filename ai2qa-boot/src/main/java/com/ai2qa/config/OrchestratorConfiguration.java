package com.ai2qa.config;

import com.ai2qa.application.port.OrchestratorConfigProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for orchestrator behavior and retry limits.
 *
 * <p>These settings control how the test execution engine handles retries,
 * obstacle clearing, and safety limits. All values can be overridden via
 * application.yml without recompiling.
 *
 * <p>Example configuration:
 * <pre>
 * ai2qa:
 *   orchestrator:
 *     max-retries: 3
 *     max-obstacle-clear-attempts: 3
 *     max-loop-iterations: 100
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "ai2qa.orchestrator")
public class OrchestratorConfiguration implements OrchestratorConfigProvider {

    /**
     * Maximum retry attempts per action step.
     * After this many retries, optional steps are skipped, required steps abort.
     */
    private int maxRetries = 3;

    /**
     * Maximum attempts to clear blocking obstacles before each step.
     */
    private int maxObstacleClearAttempts = 3;

    /**
     * Global safety limit - terminate test if exceeded.
     * A 12-step test with all retries = 48 iterations, so 50 is reasonable.
     */
    private int maxLoopIterations = 50;

    /**
     * Overall test run timeout in minutes.
     * Prevents tests from hanging forever due to AI API timeouts, Chrome hangs, etc.
     */
    private int testTimeoutMinutes = 30;

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public int getMaxObstacleClearAttempts() {
        return maxObstacleClearAttempts;
    }

    public void setMaxObstacleClearAttempts(int maxObstacleClearAttempts) {
        this.maxObstacleClearAttempts = maxObstacleClearAttempts;
    }

    @Override
    public int getMaxLoopIterations() {
        return maxLoopIterations;
    }

    public void setMaxLoopIterations(int maxLoopIterations) {
        this.maxLoopIterations = maxLoopIterations;
    }

    @Override
    public int getTestTimeoutMinutes() {
        return testTimeoutMinutes;
    }

    public void setTestTimeoutMinutes(int testTimeoutMinutes) {
        this.testTimeoutMinutes = testTimeoutMinutes;
    }

    @Override
    public String toString() {
        return String.format(
                "OrchestratorConfiguration{maxRetries=%d, maxObstacleClearAttempts=%d, maxLoopIterations=%d, testTimeoutMinutes=%d}",
                maxRetries, maxObstacleClearAttempts, maxLoopIterations, testTimeoutMinutes
        );
    }
}
