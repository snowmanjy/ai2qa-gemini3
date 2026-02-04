package com.ai2qa.application.persona;

import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.repository.PersonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central resolution service for persona definitions.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>In-memory cache</li>
 *   <li>Database (via PersonaRepository)</li>
 *   <li>Fallback to TestPersona enum (backward compatibility)</li>
 *   <li>Default: STANDARD persona</li>
 * </ol>
 */
@Service
public class PersonaRegistry {

    private static final Logger log = LoggerFactory.getLogger(PersonaRegistry.class);

    private final PersonaRepository personaRepository;
    private final ConcurrentHashMap<String, PersonaDefinition> cache = new ConcurrentHashMap<>();

    public PersonaRegistry(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    /**
     * Resolves a persona by name.
     *
     * @param name The persona name (case-insensitive), may be null or blank
     * @return The resolved PersonaDefinition (never null; defaults to STANDARD)
     */
    public PersonaDefinition resolve(String name) {
        String normalizedName = normalizeName(name);

        // 1. Check cache
        PersonaDefinition cached = cache.get(normalizedName);
        if (cached != null) {
            return cached;
        }

        // 2. Check database
        Optional<PersonaDefinition> fromDb = personaRepository.findByName(normalizedName);
        if (fromDb.isPresent()) {
            cache.put(normalizedName, fromDb.get());
            return fromDb.get();
        }

        // 3. Fallback to TestPersona enum
        Optional<PersonaDefinition> fromEnum = resolveFromEnum(normalizedName);
        if (fromEnum.isPresent()) {
            cache.put(normalizedName, fromEnum.get());
            return fromEnum.get();
        }

        // 4. Default to STANDARD
        log.info("Persona '{}' not found, defaulting to {}", name, TestPersona.DEFAULT_NAME);
        return resolve(TestPersona.DEFAULT_NAME);
    }

    /**
     * Lists all active personas from database, supplemented by enum fallbacks.
     *
     * @return List of all active persona definitions
     */
    public List<PersonaDefinition> listActive() {
        List<PersonaDefinition> active = new ArrayList<>(personaRepository.findAllActive());

        // Add enum personas that aren't in DB yet
        for (TestPersona enumPersona : TestPersona.values()) {
            boolean existsInDb = active.stream()
                    .anyMatch(p -> p.name().equalsIgnoreCase(enumPersona.name()));
            if (!existsInDb) {
                buildFromEnum(enumPersona).ifPresent(active::add);
            }
        }

        return List.copyOf(active);
    }

    /**
     * Invalidates the entire cache.
     *
     * <p>Call this after admin changes to persona definitions.
     */
    public void invalidateCache() {
        cache.clear();
        log.info("Persona cache invalidated");
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return TestPersona.DEFAULT_NAME;
        }
        return name.trim().toUpperCase();
    }

    private Optional<PersonaDefinition> resolveFromEnum(String name) {
        for (TestPersona persona : TestPersona.values()) {
            if (persona.name().equalsIgnoreCase(name)) {
                return buildFromEnum(persona);
            }
        }
        return Optional.empty();
    }

    private Optional<PersonaDefinition> buildFromEnum(TestPersona persona) {
        return PersonaDefinitionFactory.create(
                persona.name(),
                formatDisplayName(persona.name()),
                persona.getTemperature(),
                persona.getSystemPrompt(),
                PersonaSource.BUILTIN
        );
    }

    private String formatDisplayName(String enumName) {
        for (TestPersona persona : TestPersona.values()) {
            if (persona.name().equalsIgnoreCase(enumName)) {
                return persona.getDisplayName();
            }
        }
        return enumName;
    }
}
