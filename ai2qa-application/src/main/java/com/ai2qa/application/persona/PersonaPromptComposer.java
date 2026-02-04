package com.ai2qa.application.persona;

import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.SkillReference;
import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Composes the final system prompt from a persona's base prompt,
 * skill instructions (sorted by priority), and memory context.
 */
@Service
public class PersonaPromptComposer {

    private static final Logger log = LoggerFactory.getLogger(PersonaPromptComposer.class);

    private final SkillRepository skillRepository;

    public PersonaPromptComposer(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Composes a complete system prompt from persona, skills, and memory context.
     *
     * @param persona       The persona definition
     * @param memoryContext Optional memory context from Global Hippocampus (may be null or blank)
     * @return The composed system prompt
     */
    public String compose(PersonaDefinition persona, String memoryContext) {
        StringBuilder prompt = new StringBuilder(persona.systemPrompt());

        // Append skill instructions sorted by priority (lower = first)
        List<SkillReference> sortedSkills = persona.skills().stream()
                .sorted(Comparator.comparingInt(SkillReference::priority))
                .toList();

        if (!sortedSkills.isEmpty()) {
            prompt.append("\n\n[ADDITIONAL SKILLS]\n");
            for (SkillReference ref : sortedSkills) {
                skillRepository.findById(ref.skillId())
                        .map(Skill::instructions)
                        .ifPresent(instructions -> {
                            prompt.append(String.format("- [%s]: %s\n", ref.skillName(), instructions));
                            log.debug("Composed skill '{}' (priority {}) into prompt", ref.skillName(), ref.priority());
                        });
            }
        }

        // Append memory context
        if (memoryContext != null && !memoryContext.isBlank()) {
            prompt.append(memoryContext);
        }

        return prompt.toString();
    }
}
