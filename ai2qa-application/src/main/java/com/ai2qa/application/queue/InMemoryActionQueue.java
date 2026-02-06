package com.ai2qa.application.queue;

import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.port.ActionQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of ActionQueuePort.
 *
 * <p>This is a simplified in-memory version.
 * Uses ConcurrentHashMap with LinkedList for thread-safe queue operations.
 *
 * <p>Note: pushFront/pushFrontAll are implemented but the orchestrator
 * uses simple retry (re-add to end) instead of repair injection.
 */
@Component
public class InMemoryActionQueue implements ActionQueuePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryActionQueue.class);

    private final Map<String, LinkedList<ActionStep>> queues = new ConcurrentHashMap<>();

    @Override
    public void push(TestRunId testRunId, ActionStep step) {
        String key = testRunId.value().toString();
        queues.computeIfAbsent(key, k -> new LinkedList<>()).addLast(step);
        log.debug("Pushed step {} to back of queue {}", step.stepId(), testRunId);
    }

    @Override
    public void pushAll(TestRunId testRunId, List<ActionStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.computeIfAbsent(key, k -> new LinkedList<>());
        queue.addAll(steps);
        log.debug("Pushed {} steps to back of queue {}", steps.size(), testRunId);
    }

    @Override
    public void pushFront(TestRunId testRunId, ActionStep step) {
        // Simplified: just add to front (no complex repair injection)
        String key = testRunId.value().toString();
        queues.computeIfAbsent(key, k -> new LinkedList<>()).addFirst(step);
        log.debug("Pushed step {} to front of queue {}", step.stepId(), testRunId);
    }

    @Override
    public void pushFrontAll(TestRunId testRunId, List<ActionStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        // Simplified: just add to front in reverse order
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.computeIfAbsent(key, k -> new LinkedList<>());
        // Add in reverse order to maintain original order at front
        for (int i = steps.size() - 1; i >= 0; i--) {
            queue.addFirst(steps.get(i));
        }
        log.debug("Pushed {} steps to front of queue {}", steps.size(), testRunId);
    }

    @Override
    public Optional<ActionStep> pop(TestRunId testRunId) {
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        ActionStep step = queue.removeFirst();
        log.debug("Popped step {} from queue {}", step.stepId(), testRunId);
        return Optional.of(step);
    }

    @Override
    public Optional<ActionStep> peek(TestRunId testRunId) {
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queue.peekFirst());
    }

    @Override
    public int size(TestRunId testRunId) {
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.get(key);
        return queue != null ? queue.size() : 0;
    }

    @Override
    public boolean isEmpty(TestRunId testRunId) {
        return size(testRunId) == 0;
    }

    @Override
    public void clear(TestRunId testRunId) {
        String key = testRunId.value().toString();
        queues.remove(key);
        log.debug("Cleared queue {}", testRunId);
    }

    @Override
    public List<ActionStep> getAll(TestRunId testRunId) {
        String key = testRunId.value().toString();
        LinkedList<ActionStep> queue = queues.get(key);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(queue);
    }
}
