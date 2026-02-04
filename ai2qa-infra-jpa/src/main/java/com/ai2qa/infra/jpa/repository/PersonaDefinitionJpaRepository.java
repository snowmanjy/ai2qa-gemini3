package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.PersonaDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PersonaDefinitionEntity.
 */
@Repository
public interface PersonaDefinitionJpaRepository extends JpaRepository<PersonaDefinitionEntity, UUID> {

    /**
     * Finds a persona definition by its unique name.
     */
    Optional<PersonaDefinitionEntity> findByName(String name);

    /**
     * Finds all active persona definitions.
     */
    List<PersonaDefinitionEntity> findByActiveTrue();
}
