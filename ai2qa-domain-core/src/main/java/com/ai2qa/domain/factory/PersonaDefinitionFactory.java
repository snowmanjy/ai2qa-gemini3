package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaId;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.model.persona.SkillReference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Factory for creating and reconstituting PersonaDefinition instances.
 *
 * <p>All validation lives here, not in the record constructor.
 */
public final class PersonaDefinitionFactory {

    private PersonaDefinitionFactory() {
    }

    /**
     * Creates a new PersonaDefinition with validation.
     *
     * @param name         Unique persona name (e.g., "STANDARD")
     * @param displayName  Human-readable display name
     * @param temperature  AI temperature (0.0 to 2.0)
     * @param systemPrompt The system prompt for AI behavior
     * @param source       The source of this persona
     * @return Optional containing the persona if valid, empty otherwise
     */
    public static Optional<PersonaDefinition> create(
            String name,
            String displayName,
            double temperature,
            String systemPrompt,
            PersonaSource source) {

        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        if (temperature < 0.0 || temperature > 2.0) {
            return Optional.empty();
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return Optional.empty();
        }
        if (source == null) {
            return Optional.empty();
        }

        return Optional.of(new PersonaDefinition(
                new PersonaId(UUID.randomUUID()),
                name,
                displayName,
                temperature,
                systemPrompt,
                List.of(),
                source,
                true
        ));
    }

    /**
     * Reconstitutes a PersonaDefinition from persistence layer.
     *
     * <p>Used by infrastructure adapters to restore state from the database.
     * Skips validation since data is already persisted and trusted.
     *
     * @return The reconstituted PersonaDefinition
     */
    public static PersonaDefinition reconstitute(
            UUID id,
            String name,
            String displayName,
            double temperature,
            String systemPrompt,
            List<SkillReference> skills,
            PersonaSource source,
            boolean active) {

        return new PersonaDefinition(
                new PersonaId(id),
                name,
                displayName,
                temperature,
                systemPrompt,
                skills,
                source,
                active
        );
    }
}
