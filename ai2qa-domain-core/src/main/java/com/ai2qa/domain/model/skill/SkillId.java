package com.ai2qa.domain.model.skill;

import java.util.UUID;

/**
 * Value object representing a unique Skill identifier.
 *
 * <p>Immutable and identity-based equality.
 */
public record SkillId(UUID value) {

    @Override
    public String toString() {
        return value.toString();
    }
}
