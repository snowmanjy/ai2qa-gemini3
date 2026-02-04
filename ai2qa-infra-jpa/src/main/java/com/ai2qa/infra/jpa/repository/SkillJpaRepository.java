package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for SkillEntity.
 */
@Repository
public interface SkillJpaRepository extends JpaRepository<SkillEntity, UUID> {

    /**
     * Finds a skill by its unique name.
     */
    Optional<SkillEntity> findByName(String name);

    /**
     * Finds all skills with the given status.
     */
    List<SkillEntity> findByStatus(String status);
}
