package com.ai2qa.domain.model.skill;

import java.time.Instant;
import java.util.List;

/**
 * Pure data record representing a skill that can be attached to personas.
 *
 * <p>Skills provide additional instructions that get composed into the
 * persona's system prompt during test execution.
 */
public record Skill(
        SkillId id,
        String name,
        String instructions,
        List<String> patterns,
        SkillCategory category,
        SkillStatus status,
        String sourceUrl,
        String sourceHash,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Defensive copy constructor ensures patterns list is unmodifiable.
     */
    public Skill {
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }
}
