package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.AgentMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for AgentMemoryEntity.
 */
@Repository
public interface AgentMemoryJpaRepository extends JpaRepository<AgentMemoryEntity, String> {

    /**
     * Finds all memory entries ordered by last updated.
     */
    List<AgentMemoryEntity> findAllByOrderByLastUpdatedAtDesc();

    /**
     * Finds entries where insight text exceeds a certain length (for compression).
     */
    @Query("SELECT m FROM AgentMemoryEntity m WHERE LENGTH(m.insightText) > :minLength")
    List<AgentMemoryEntity> findEntriesExceedingLength(int minLength);
}
