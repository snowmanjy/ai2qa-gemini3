package com.ai2qa.domain.model.knowledge;

import java.util.Optional;
import java.util.UUID;

/**
 * Value object for flow strategy identifiers.
 */
public record FlowStrategyId(UUID value) {

    /**
     * Creates a new random FlowStrategyId.
     */
    public static FlowStrategyId generate() {
        return new FlowStrategyId(UUID.randomUUID());
    }

    /**
     * Creates a FlowStrategyId from a string representation.
     */
    public static Optional<FlowStrategyId> fromString(String id) {
        return Optional.ofNullable(id)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return new FlowStrategyId(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
    }

    /**
     * Reconstitutes from database storage.
     */
    public static FlowStrategyId reconstitute(UUID value) {
        return new FlowStrategyId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
