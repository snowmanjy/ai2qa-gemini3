package com.ai2qa.application.queue;

import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.port.DoneQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory implementation of DoneQueuePort.
 *
 * <p>This is a simplified in-memory version.
 * Records executed steps in memory for the test run history.
 */
@Component
public class InMemoryDoneQueue implements DoneQueuePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryDoneQueue.class);

    private final Map<String, List<ExecutedStep>> histories = new ConcurrentHashMap<>();

    @Override
    public void record(TestRunId testRunId, ExecutedStep executedStep) {
        String key = testRunId.value().toString();
        histories.computeIfAbsent(key, k -> new ArrayList<>()).add(executedStep);
        log.debug("Recorded step {} in history for {}", executedStep.step().stepId(), testRunId);
    }

    @Override
    public List<ExecutedStep> getHistory(TestRunId testRunId) {
        String key = testRunId.value().toString();
        List<ExecutedStep> history = histories.get(key);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(history);
    }

    @Override
    public List<ExecutedStep> getRecentHistory(TestRunId testRunId, int count) {
        List<ExecutedStep> history = getHistory(testRunId);
        if (history.isEmpty() || count <= 0) {
            return List.of();
        }
        int startIndex = Math.max(0, history.size() - count);
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    @Override
    public Optional<ExecutedStep> getLastStep(TestRunId testRunId) {
        List<ExecutedStep> history = getHistory(testRunId);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1));
    }

    @Override
    public int size(TestRunId testRunId) {
        String key = testRunId.value().toString();
        List<ExecutedStep> history = histories.get(key);
        return history != null ? history.size() : 0;
    }

    @Override
    public void clear(TestRunId testRunId) {
        String key = testRunId.value().toString();
        histories.remove(key);
        log.debug("Cleared history for {}", testRunId);
    }
}
