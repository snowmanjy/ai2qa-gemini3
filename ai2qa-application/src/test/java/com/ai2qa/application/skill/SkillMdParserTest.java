package com.ai2qa.application.skill;

import com.ai2qa.domain.model.skill.SkillMdContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillMdParser")
class SkillMdParserTest {

    @Nested
    @DisplayName("parse()")
    class ParseTests {

        @Test
        @DisplayName("valid SKILL.md with full frontmatter parses correctly")
        void validSkillMdParsesCorrectly() {
            String content = """
                    ---
                    name: webapp-testing
                    description: Browser-based testing patterns
                    license: MIT
                    metadata:
                      author: ai2qa
                      version: "1.0"
                    ---
                    # Webapp Testing

                    Use Playwright patterns for DOM verification.
                    """;

            Optional<SkillMdContent> result = SkillMdParser.parse(content);

            assertThat(result).isPresent();
            SkillMdContent parsed = result.get();
            assertThat(parsed.name()).isEqualTo("webapp-testing");
            assertThat(parsed.description()).isEqualTo("Browser-based testing patterns");
            assertThat(parsed.license()).isEqualTo("MIT");
            assertThat(parsed.metadata()).containsEntry("author", "ai2qa");
            assertThat(parsed.metadata()).containsEntry("version", "1.0");
            assertThat(parsed.markdownBody()).contains("Webapp Testing");
            assertThat(parsed.markdownBody()).contains("Playwright patterns");
        }

        @Test
        @DisplayName("missing frontmatter returns empty")
        void missingFrontmatterReturnsEmpty() {
            String content = """
                    # Just Markdown

                    No frontmatter here.
                    """;

            assertThat(SkillMdParser.parse(content)).isEmpty();
        }

        @Test
        @DisplayName("missing closing --- returns empty")
        void missingClosingDelimiterReturnsEmpty() {
            String content = """
                    ---
                    name: broken-skill
                    description: Missing closing delimiter

                    # This is the body but no closing ---
                    """;

            assertThat(SkillMdParser.parse(content)).isEmpty();
        }

        @Test
        @DisplayName("empty body after frontmatter returns empty")
        void emptyBodyReturnsEmpty() {
            String content = """
                    ---
                    name: empty-body
                    description: No markdown body
                    ---
                    """;

            assertThat(SkillMdParser.parse(content)).isEmpty();
        }

        @Test
        @DisplayName("minimal frontmatter with name only works with defaults")
        void minimalFrontmatterWorksWithDefaults() {
            String content = """
                    ---
                    name: minimal-skill
                    ---
                    # Minimal Content

                    Just the basics.
                    """;

            Optional<SkillMdContent> result = SkillMdParser.parse(content);

            assertThat(result).isPresent();
            SkillMdContent parsed = result.get();
            assertThat(parsed.name()).isEqualTo("minimal-skill");
            assertThat(parsed.description()).isEmpty();
            assertThat(parsed.license()).isEmpty();
            assertThat(parsed.metadata()).isEmpty();
            assertThat(parsed.markdownBody()).contains("Minimal Content");
        }

        @Test
        @DisplayName("null input returns empty")
        void nullInputReturnsEmpty() {
            assertThat(SkillMdParser.parse(null)).isEmpty();
        }

        @Test
        @DisplayName("blank input returns empty")
        void blankInputReturnsEmpty() {
            assertThat(SkillMdParser.parse("   ")).isEmpty();
        }

        @Test
        @DisplayName("frontmatter without name returns empty")
        void frontmatterWithoutNameReturnsEmpty() {
            String content = """
                    ---
                    description: No name field
                    license: MIT
                    ---
                    # Content

                    Some body text.
                    """;

            assertThat(SkillMdParser.parse(content)).isEmpty();
        }
    }

    @Nested
    @DisplayName("splitFrontmatter()")
    class SplitFrontmatterTests {

        @Test
        @DisplayName("valid content splits into yaml and body")
        void validContentSplitsCorrectly() {
            String content = """
                    ---
                    name: test
                    ---
                    # Body
                    """;

            Optional<String[]> result = SkillMdParser.splitFrontmatter(content);

            assertThat(result).isPresent();
            assertThat(result.get()[0]).isEqualTo("name: test");
            assertThat(result.get()[1]).contains("Body");
        }

        @Test
        @DisplayName("content not starting with --- returns empty")
        void noStartingDelimiterReturnsEmpty() {
            assertThat(SkillMdParser.splitFrontmatter("no delimiters")).isEmpty();
        }
    }
}
