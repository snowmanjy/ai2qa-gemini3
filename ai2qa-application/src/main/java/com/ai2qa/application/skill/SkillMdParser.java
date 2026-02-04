package com.ai2qa.application.skill;

import com.ai2qa.domain.model.skill.SkillMdContent;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses SKILL.md files into {@link SkillMdContent} records.
 *
 * <p>Extracts YAML frontmatter (between {@code ---} delimiters) and the
 * remaining markdown body. No Spring dependency — pure utility class.
 */
public final class SkillMdParser {

    private SkillMdParser() {
    }

    /**
     * Parses raw SKILL.md content into a structured record.
     *
     * @param rawContent The full text content of a SKILL.md file
     * @return Optional containing parsed content, or empty for malformed input
     */
    public static Optional<SkillMdContent> parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }

        return splitFrontmatter(rawContent)
                .flatMap(SkillMdParser::parseFrontmatter);
    }

    /**
     * Splits content into YAML frontmatter and markdown body.
     *
     * <p>Expects the content to start with {@code ---}, followed by YAML,
     * followed by a closing {@code ---}, then the markdown body.
     *
     * @param content The raw content
     * @return Optional containing [yamlSection, markdownBody], or empty if malformed
     */
    static Optional<String[]> splitFrontmatter(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("---")) {
            return Optional.empty();
        }

        int secondDelimiter = trimmed.indexOf("---", 3);
        if (secondDelimiter < 0) {
            return Optional.empty();
        }

        String yamlSection = trimmed.substring(3, secondDelimiter).trim();
        String markdownBody = trimmed.substring(secondDelimiter + 3).trim();

        if (markdownBody.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new String[]{yamlSection, markdownBody});
    }

    @SuppressWarnings("unchecked")
    private static Optional<SkillMdContent> parseFrontmatter(String[] parts) {
        String yamlSection = parts[0];
        String markdownBody = parts[1];

        Map<String, Object> yamlMap;
        try {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(yamlSection);
            if (!(parsed instanceof Map)) {
                return Optional.empty();
            }
            yamlMap = (Map<String, Object>) parsed;
        } catch (Exception e) {
            return Optional.empty();
        }

        return extractString(yamlMap, "name")
                .filter(n -> !n.isBlank())
                .map(name -> {
                    String description = extractString(yamlMap, "description").orElse("");
                    String license = extractString(yamlMap, "license").orElse("");
                    Map<String, String> metadata = extractMetadata(yamlMap);
                    return new SkillMdContent(name, description, license, metadata, markdownBody);
                });
    }

    private static Optional<String> extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> extractMetadata(Map<String, Object> yamlMap) {
        Object metaObj = yamlMap.get("metadata");
        if (!(metaObj instanceof Map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> rawMeta = (Map<String, Object>) metaObj;
        Map<String, String> result = new LinkedHashMap<>();
        rawMeta.forEach((k, v) -> {
            if (v != null) {
                result.put(k, v.toString());
            }
        });
        return result;
    }
}
