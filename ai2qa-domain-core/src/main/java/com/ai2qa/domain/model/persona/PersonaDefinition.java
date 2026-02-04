package com.ai2qa.domain.model.persona;

import java.util.List;

/**
 * Pure data record representing a persona definition.
 *
 * <p>A persona shapes AI agent behavior during test execution by providing
 * a system prompt, temperature setting, and optional skill references.
 *
 * <p>This is the dynamic replacement for the hardcoded {@code TestPersona} enum,
 * backed by database persistence.
 */
public record PersonaDefinition(
        PersonaId id,
        String name,
        String displayName,
        double temperature,
        String systemPrompt,
        List<SkillReference> skills,
        PersonaSource source,
        boolean active
) {

    /**
     * Defensive copy constructor ensures skills list is unmodifiable.
     */
    public PersonaDefinition {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
