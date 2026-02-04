package com.ai2qa.domain.model;

/**
 * Execution mode for test runs.
 *
 * <p>Determines where browser automation commands are executed:
 * <ul>
 *   <li>CLOUD - Run on AI2QA cloud infrastructure (default)</li>
 *   <li>LOCAL_AGENT - Run on user's local machine via connected agent</li>
 * </ul>
 */
public enum ExecutionMode {

    /**
     * Execute on AI2QA cloud infrastructure.
     * Uses the embedded browser instance.
     */
    CLOUD,

    /**
     * Execute on user's local machine.
     * Commands are routed through WebSocket to a connected local agent.
     */
    LOCAL_AGENT;

    /**
     * Returns true if this mode requires a connected local agent.
     */
    public boolean requiresLocalAgent() {
        return this == LOCAL_AGENT;
    }
}
