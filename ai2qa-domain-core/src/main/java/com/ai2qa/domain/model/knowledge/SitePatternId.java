package com.ai2qa.domain.model.knowledge;

import java.util.Optional;
import java.util.UUID;

/**
 * Value object for site pattern identifiers.
 */
public record SitePatternId(UUID value) {

    /**
     * Creates a new random SitePatternId.
     */
    public static SitePatternId generate() {
        return new SitePatternId(UUID.randomUUID());
    }

    /**
     * Creates a SitePatternId from a string representation.
     */
    public static Optional<SitePatternId> fromString(String id) {
        return Optional.ofNullable(id)
                .filter(s -> !s.isBlank())
                .map(s -> {
                    try {
                        return new SitePatternId(UUID.fromString(s));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                });
    }

    /**
     * Reconstitutes from database storage.
     */
    public static SitePatternId reconstitute(UUID value) {
        return new SitePatternId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
