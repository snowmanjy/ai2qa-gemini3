package com.ai2qa.application.queue;

import com.ai2qa.domain.port.QueueStatsPort;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory implementation of QueueStatsPort.
 *
 * <p>This is a simplified in-memory version.
 * Returns -1 (unavailable) since we don't track global stats in-memory.
 */
@Component
public class InMemoryQueueStats implements QueueStatsPort {

    private final InMemoryActionQueue actionQueue;
    private final InMemoryDoneQueue doneQueue;

    public InMemoryQueueStats(InMemoryActionQueue actionQueue, InMemoryDoneQueue doneQueue) {
        this.actionQueue = actionQueue;
        this.doneQueue = doneQueue;
    }

    @Override
    public int countActionQueues() {
        // Return -1 (unavailable) - the in-memory implementation doesn't expose queue count easily
        return -1;
    }

    @Override
    public int countDoneQueues() {
        // Return -1 (unavailable)
        return -1;
    }
}
