package com.ai2qa.domain.model.skill;

import java.util.Map;

/**
 * Pure data record representing parsed SKILL.md content.
 *
 * <p>Contains the YAML frontmatter fields and the markdown body
 * extracted from a SKILL.md file.
 */
public record SkillMdContent(
        String name,
        String description,
        String license,
        Map<String, String> metadata,
        String markdownBody
) {

    /**
     * Defensive copy constructor ensures metadata map is unmodifiable.
     */
    public SkillMdContent {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
