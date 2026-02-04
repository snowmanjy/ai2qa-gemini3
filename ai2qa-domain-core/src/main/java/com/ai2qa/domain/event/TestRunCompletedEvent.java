package com.ai2qa.domain.event;

import com.ai2qa.domain.model.TestRun;

import java.time.Instant;

/**
 * Domain event published when a test run completes (success or failure).
 *
 * <p>This is a pure domain event with no framework dependencies.
 * It can be adapted to Spring events, message queues, etc. in the infrastructure layer.
 *
 * @param testRun     The completed test run
 * @param completedAt When the test run completed
 */
public record TestRunCompletedEvent(
        TestRun testRun,
        Instant completedAt
) {
    /**
     * Creates a completion event for the given test run.
     */
    public static TestRunCompletedEvent of(TestRun testRun) {
        return new TestRunCompletedEvent(
                testRun,
                testRun.getCompletedAt().orElse(Instant.now())
        );
    }
}
