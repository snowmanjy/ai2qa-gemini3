package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.OrchestratorConfigProvider;

/**
 * Test implementation of OrchestratorConfigProvider with default values.
 */
public class TestOrchestratorConfig implements OrchestratorConfigProvider {

    private int maxRetries = 3;
    private int maxObstacleClearAttempts = 3;
    private int maxLoopIterations = 50;
    private int testTimeoutMinutes = 30;

    @Override
    public int getMaxRetries() {
        return maxRetries;
    }

    @Override
    public int getMaxObstacleClearAttempts() {
        return maxObstacleClearAttempts;
    }

    @Override
    public int getMaxLoopIterations() {
        return maxLoopIterations;
    }

    @Override
    public int getTestTimeoutMinutes() {
        return testTimeoutMinutes;
    }

    public TestOrchestratorConfig withMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public TestOrchestratorConfig withMaxObstacleClearAttempts(int maxObstacleClearAttempts) {
        this.maxObstacleClearAttempts = maxObstacleClearAttempts;
        return this;
    }

    public TestOrchestratorConfig withMaxLoopIterations(int maxLoopIterations) {
        this.maxLoopIterations = maxLoopIterations;
        return this;
    }

    public TestOrchestratorConfig withTestTimeoutMinutes(int testTimeoutMinutes) {
        this.testTimeoutMinutes = testTimeoutMinutes;
        return this;
    }
}
