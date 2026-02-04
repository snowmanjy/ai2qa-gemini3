package com.ai2qa.domain.model;

import java.util.UUID;

/**
 * Value object representing a unique TestRun identifier.
 *
 * <p>Immutable and identity-based equality.
 */
public record TestRunId(UUID value) {

    @Override
    public String toString() {
        return value.toString();
    }
}
