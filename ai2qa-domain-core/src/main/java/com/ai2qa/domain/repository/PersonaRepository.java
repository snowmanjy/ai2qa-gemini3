package com.ai2qa.domain.repository;

import com.ai2qa.domain.model.persona.PersonaDefinition;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PersonaDefinition aggregate.
 *
 * <p>Implementations must persist persona definitions and support
 * lookup by name for resolution during test execution.
 */
public interface PersonaRepository {

    /**
     * Saves or updates a persona definition.
     *
     * @param persona The persona definition to save
     * @return The saved persona definition
     */
    PersonaDefinition save(PersonaDefinition persona);

    /**
     * Finds a persona definition by its unique name.
     *
     * @param name The persona name (e.g., "STANDARD", "PERFORMANCE_HAWK")
     * @return Optional containing the persona if found
     */
    Optional<PersonaDefinition> findByName(String name);

    /**
     * Finds all active persona definitions.
     *
     * @return List of active persona definitions
     */
    List<PersonaDefinition> findAllActive();
}
