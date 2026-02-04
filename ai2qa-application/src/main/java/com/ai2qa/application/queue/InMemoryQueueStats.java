package com.ai2qa.application.queue;

import com.ai2qa.domain.port.QueueStatsPort;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory implementation of QueueStatsPort.
 *
 * <p>This is a simplified version for the hackathon demo.
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
        // For simplicity, return -1 (unavailable) in hackathon version
        // The in-memory implementation doesn't expose queue count easily
        return -1;
    }

    @Override
    public int countDoneQueues() {
        // For simplicity, return -1 (unavailable) in hackathon version
        return -1;
    }
}
