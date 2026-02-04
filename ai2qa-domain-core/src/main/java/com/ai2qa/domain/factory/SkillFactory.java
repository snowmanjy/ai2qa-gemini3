package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.model.skill.SkillId;
import com.ai2qa.domain.model.skill.SkillStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Factory for creating and reconstituting Skill instances.
 *
 * <p>All validation lives here, not in the record constructor.
 */
public final class SkillFactory {

    private SkillFactory() {
    }

    /**
     * Creates a new Skill with validation.
     *
     * @param name         Unique skill name
     * @param instructions The skill instructions to inject into prompts
     * @param category     The skill category
     * @return Optional containing the skill if valid, empty otherwise
     */
    public static Optional<Skill> create(
            String name,
            String instructions,
            SkillCategory category) {

        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (instructions == null || instructions.isBlank()) {
            return Optional.empty();
        }
        if (category == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        return Optional.of(new Skill(
                new SkillId(UUID.randomUUID()),
                name,
                instructions,
                List.of(),
                category,
                SkillStatus.ACTIVE,
                null,
                null,
                now,
                now
        ));
    }

    /**
     * Creates a new Skill from an external source (e.g., absorbed from a URL).
     *
     * <p>External skills start in DRAFT status and require review.
     *
     * @param name         Unique skill name
     * @param instructions The skill instructions
     * @param category     The skill category
     * @param sourceUrl    URL where the skill was absorbed from
     * @param sourceHash   Hash of the source content for change detection
     * @return Optional containing the skill if valid, empty otherwise
     */
    public static Optional<Skill> fromExternalSource(
            String name,
            String instructions,
            SkillCategory category,
            String sourceUrl,
            String sourceHash) {

        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (instructions == null || instructions.isBlank()) {
            return Optional.empty();
        }
        if (category == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        return Optional.of(new Skill(
                new SkillId(UUID.randomUUID()),
                name,
                instructions,
                List.of(),
                category,
                SkillStatus.DRAFT,
                sourceUrl,
                sourceHash,
                now,
                now
        ));
    }

    /**
     * Reconstitutes a Skill from persistence layer.
     *
     * <p>Used by infrastructure adapters to restore state from the database.
     *
     * @return The reconstituted Skill
     */
    public static Skill reconstitute(
            UUID id,
            String name,
            String instructions,
            List<String> patterns,
            SkillCategory category,
            SkillStatus status,
            String sourceUrl,
            String sourceHash,
            Instant createdAt,
            Instant updatedAt) {

        return new Skill(
                new SkillId(id),
                name,
                instructions,
                patterns,
                category,
                status,
                sourceUrl,
                sourceHash,
                createdAt,
                updatedAt
        );
    }
}
