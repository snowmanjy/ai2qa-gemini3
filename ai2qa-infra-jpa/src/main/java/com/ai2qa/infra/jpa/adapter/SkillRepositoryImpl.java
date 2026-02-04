package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.factory.SkillFactory;
import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.model.skill.SkillId;
import com.ai2qa.domain.model.skill.SkillStatus;
import com.ai2qa.domain.repository.SkillRepository;
import com.ai2qa.infra.jpa.entity.SkillEntity;
import com.ai2qa.infra.jpa.repository.SkillJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of SkillRepository.
 *
 * <p>Follows the check-exists-first pattern to avoid
 * NonUniqueObjectException with JPA session management.
 */
@Repository
public class SkillRepositoryImpl implements SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(SkillRepositoryImpl.class);

    private final SkillJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public SkillRepositoryImpl(SkillJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Skill save(Skill skill) {
        SkillEntity entity = jpaRepository.findById(skill.id().value())
                .map(existing -> {
                    existing.setName(skill.name());
                    existing.setInstructions(skill.instructions());
                    existing.setPatterns(serializePatterns(skill.patterns()));
                    existing.setCategory(skill.category().name());
                    existing.setStatus(skill.status().name());
                    existing.setSourceUrl(skill.sourceUrl());
                    existing.setSourceHash(skill.sourceHash());
                    existing.setUpdatedAt(Instant.now());
                    return existing;
                })
                .orElseGet(() -> toEntity(skill));

        SkillEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Skill> findById(SkillId id) {
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<Skill> findByName(String name) {
        return jpaRepository.findByName(name).map(this::toDomain);
    }

    @Override
    public List<Skill> findByStatus(SkillStatus status) {
        return jpaRepository.findByStatus(status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Skill> findAllActive() {
        return jpaRepository.findByStatus(SkillStatus.ACTIVE.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Skill> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private SkillEntity toEntity(Skill skill) {
        return new SkillEntity(
                skill.id().value(),
                skill.name(),
                skill.instructions(),
                serializePatterns(skill.patterns()),
                skill.category().name(),
                skill.status().name(),
                skill.sourceUrl(),
                skill.sourceHash(),
                skill.createdAt(),
                skill.updatedAt()
        );
    }

    private Skill toDomain(SkillEntity entity) {
        return SkillFactory.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getInstructions(),
                deserializePatterns(entity.getPatterns()),
                parseCategory(entity.getCategory()),
                parseStatus(entity.getStatus()),
                entity.getSourceUrl(),
                entity.getSourceHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String serializePatterns(List<String> patterns) {
        try {
            return objectMapper.writeValueAsString(patterns);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize patterns, defaulting to empty array: {}", e.getMessage());
            return "[]";
        }
    }

    private List<String> deserializePatterns(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize patterns, defaulting to empty: {}", e.getMessage());
            return List.of();
        }
    }

    private SkillCategory parseCategory(String category) {
        try {
            return SkillCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            return SkillCategory.TESTING;
        }
    }

    private SkillStatus parseStatus(String status) {
        try {
            return SkillStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return SkillStatus.DRAFT;
        }
    }
}
