package com.ai2qa.domain.port;

/**
 * Port interface for retrieving queue statistics.
 *
 * <p>Provides aggregate counts of action and done queues
 * across all test runs for system health monitoring.
 */
public interface QueueStatsPort {

    /**
     * Counts the number of active action queues.
     *
     * @return Number of active action queues, or -1 if unavailable
     */
    int countActionQueues();

    /**
     * Counts the number of active done queues.
     *
     * @return Number of active done queues, or -1 if unavailable
     */
    int countDoneQueues();
}
