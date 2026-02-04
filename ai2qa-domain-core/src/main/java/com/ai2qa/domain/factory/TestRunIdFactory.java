package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.TestRunId;

import java.util.Optional;
import java.util.UUID;

/**
 * Factory for TestRunId creation and parsing.
 */
public final class TestRunIdFactory {

    private TestRunIdFactory() {
    }

    public static TestRunId generate() {
        return new TestRunId(UUID.randomUUID());
    }

    public static Optional<TestRunId> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TestRunId(UUID.fromString(value)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
