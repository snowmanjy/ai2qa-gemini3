package com.ai2qa.domain.model.persona;

import java.util.UUID;

/**
 * Value object representing a unique PersonaDefinition identifier.
 *
 * <p>Immutable and identity-based equality.
 */
public record PersonaId(UUID value) {

    @Override
    public String toString() {
        return value.toString();
    }
}
