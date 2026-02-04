package com.ai2qa.domain.repository;

import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillId;
import com.ai2qa.domain.model.skill.SkillStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Skill aggregate.
 *
 * <p>Implementations must persist skills and support lookup by name,
 * ID, and status for composition into persona prompts.
 */
public interface SkillRepository {

    /**
     * Saves or updates a skill.
     *
     * @param skill The skill to save
     * @return The saved skill
     */
    Skill save(Skill skill);

    /**
     * Finds a skill by its ID.
     *
     * @param id The skill ID
     * @return Optional containing the skill if found
     */
    Optional<Skill> findById(SkillId id);

    /**
     * Finds a skill by its unique name.
     *
     * @param name The skill name
     * @return Optional containing the skill if found
     */
    Optional<Skill> findByName(String name);

    /**
     * Finds all skills with the given status.
     *
     * @param status The status to filter by
     * @return List of matching skills
     */
    List<Skill> findByStatus(SkillStatus status);

    /**
     * Finds all active skills.
     *
     * @return List of active skills
     */
    List<Skill> findAllActive();

    /**
     * Finds all skills regardless of status.
     *
     * @return List of all skills
     */
    List<Skill> findAll();
}
