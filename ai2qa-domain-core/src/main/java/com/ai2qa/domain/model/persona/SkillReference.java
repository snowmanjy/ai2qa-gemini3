package com.ai2qa.domain.model.persona;

import com.ai2qa.domain.model.skill.SkillId;

/**
 * Reference from a persona to a skill, with priority ordering.
 *
 * <p>Lower priority numbers are applied first in prompt composition.
 */
public record SkillReference(SkillId skillId, String skillName, int priority) {
}
