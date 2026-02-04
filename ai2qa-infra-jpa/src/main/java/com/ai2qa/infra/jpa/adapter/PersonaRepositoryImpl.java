package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.model.persona.SkillReference;
import com.ai2qa.domain.model.skill.SkillId;
import com.ai2qa.domain.repository.PersonaRepository;
import com.ai2qa.infra.jpa.entity.PersonaDefinitionEntity;
import com.ai2qa.infra.jpa.repository.PersonaDefinitionJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of PersonaRepository.
 *
 * <p>Follows the check-exists-first pattern to avoid
 * NonUniqueObjectException with JPA session management.
 */
@Repository
public class PersonaRepositoryImpl implements PersonaRepository {

    private static final Logger log = LoggerFactory.getLogger(PersonaRepositoryImpl.class);

    private final PersonaDefinitionJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public PersonaRepositoryImpl(PersonaDefinitionJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PersonaDefinition save(PersonaDefinition persona) {
        PersonaDefinitionEntity entity = jpaRepository.findById(persona.id().value())
                .map(existing -> {
                    existing.setName(persona.name());
                    existing.setDisplayName(persona.displayName());
                    existing.setTemperature(persona.temperature());
                    existing.setSystemPrompt(persona.systemPrompt());
                    existing.setSkills(serializeSkills(persona.skills()));
                    existing.setSource(persona.source().name());
                    existing.setActive(persona.active());
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> toEntity(persona));

        PersonaDefinitionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public java.util.Optional<PersonaDefinition> findByName(String name) {
        return jpaRepository.findByName(name).map(this::toDomain);
    }

    @Override
    public List<PersonaDefinition> findAllActive() {
        return jpaRepository.findByActiveTrue().stream()
                .map(this::toDomain)
                .toList();
    }

    private PersonaDefinitionEntity toEntity(PersonaDefinition persona) {
        Instant now = Instant.now();
        return new PersonaDefinitionEntity(
                persona.id().value(),
                persona.name(),
                persona.displayName(),
                persona.temperature(),
                persona.systemPrompt(),
                serializeSkills(persona.skills()),
                persona.source().name(),
                persona.active(),
                now,
                now
        );
    }

    private PersonaDefinition toDomain(PersonaDefinitionEntity entity) {
        return PersonaDefinitionFactory.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDisplayName(),
                entity.getTemperature(),
                entity.getSystemPrompt(),
                deserializeSkills(entity.getSkills()),
                parseSource(entity.getSource()),
                entity.isActive()
        );
    }

    private String serializeSkills(List<SkillReference> skills) {
        try {
            List<SkillRefDto> dtos = skills.stream()
                    .map(sr -> new SkillRefDto(
                            sr.skillId().value().toString(),
                            sr.skillName(),
                            sr.priority()))
                    .toList();
            return objectMapper.writeValueAsString(dtos);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize skills, defaulting to empty array: {}", e.getMessage());
            return "[]";
        }
    }

    private List<SkillReference> deserializeSkills(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            List<SkillRefDto> dtos = objectMapper.readValue(json, new TypeReference<>() {});
            return dtos.stream()
                    .map(dto -> new SkillReference(
                            new SkillId(UUID.fromString(dto.skillId())),
                            dto.skillName(),
                            dto.priority()))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize skills, defaulting to empty: {}", e.getMessage());
            return List.of();
        }
    }

    private PersonaSource parseSource(String source) {
        try {
            return PersonaSource.valueOf(source);
        } catch (IllegalArgumentException e) {
            return PersonaSource.BUILTIN;
        }
    }

    /**
     * Internal DTO for JSON serialization of skill references.
     */
    private record SkillRefDto(String skillId, String skillName, int priority) {
    }
}
