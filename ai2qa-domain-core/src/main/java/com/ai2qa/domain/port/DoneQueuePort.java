package com.ai2qa.domain.port;

import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestRunId;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for the done queue.
 *
 * <p>
 * Immutable log of successfully executed steps.
 * Used for "Time Machine" replay and audit trails.
 */
public interface DoneQueuePort {

    /**
     * Records a completed step to the history.
     *
     * @param testRunId    The test run identifier
     * @param executedStep The executed step to record
     */
    void record(TestRunId testRunId, ExecutedStep executedStep);

    /**
     * Returns all recorded steps for a test run.
     *
     * @param testRunId The test run identifier
     * @return List of all executed steps in order
     */
    List<ExecutedStep> getHistory(TestRunId testRunId);

    /**
     * Returns the most recent N steps.
     *
     * @param testRunId The test run identifier
     * @param count     Maximum number of steps to return
     * @return List of recent executed steps
     */
    List<ExecutedStep> getRecentHistory(TestRunId testRunId, int count);

    /**
     * Returns the last executed step.
     *
     * @param testRunId The test run identifier
     * @return The last step, or empty if no history
     */
    Optional<ExecutedStep> getLastStep(TestRunId testRunId);

    /**
     * Returns the count of executed steps.
     *
     * @param testRunId The test run identifier
     * @return Number of completed steps
     */
    int size(TestRunId testRunId);

    /**
     * Clears the history for a test run.
     *
     * @param testRunId The test run identifier
     */
    void clear(TestRunId testRunId);
}
