package com.ai2qa.domain.port;

import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.TestRunId;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for the action queue.
 *
 * <p>
 * Stores pending action steps for a test run.
 * Uses Redis Lists semantics (RPUSH/LPUSH/LPOP).
 */
public interface ActionQueuePort {

    /**
     * Pushes a step to the back of the queue (normal flow).
     *
     * @param testRunId The test run identifier
     * @param step      The step to push
     */
    void push(TestRunId testRunId, ActionStep step);

    /**
     * Pushes multiple steps to the back of the queue.
     *
     * @param testRunId The test run identifier
     * @param steps     The steps to push
     */
    void pushAll(TestRunId testRunId, List<ActionStep> steps);

    /**
     * Pushes a step to the FRONT of the queue (for self-healing/repair).
     *
     * @param testRunId The test run identifier
     * @param step      The repair step to push
     */
    void pushFront(TestRunId testRunId, ActionStep step);

    /**
     * Pushes multiple repair steps to the front of the queue.
     *
     * @param testRunId The test run identifier
     * @param steps     The repair steps to push
     */
    void pushFrontAll(TestRunId testRunId, List<ActionStep> steps);

    /**
     * Pops and returns the next step from the front of the queue.
     *
     * @param testRunId The test run identifier
     * @return The next step, or empty if queue is empty
     */
    Optional<ActionStep> pop(TestRunId testRunId);

    /**
     * Peeks at the next step without removing it.
     *
     * @param testRunId The test run identifier
     * @return The next step, or empty if queue is empty
     */
    Optional<ActionStep> peek(TestRunId testRunId);

    /**
     * Returns the current queue size.
     *
     * @param testRunId The test run identifier
     * @return Number of steps in queue
     */
    int size(TestRunId testRunId);

    /**
     * Checks if the queue is empty.
     *
     * @param testRunId The test run identifier
     * @return true if empty
     */
    boolean isEmpty(TestRunId testRunId);

    /**
     * Clears all steps from the queue.
     *
     * @param testRunId The test run identifier
     */
    void clear(TestRunId testRunId);

    /**
     * Returns all steps currently in the queue (for debugging/inspection).
     *
     * @param testRunId The test run identifier
     * @return List of all queued steps
     */
    List<ActionStep> getAll(TestRunId testRunId);
}
