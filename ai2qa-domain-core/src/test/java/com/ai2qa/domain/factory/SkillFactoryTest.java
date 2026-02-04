package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.model.skill.SkillStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SkillFactory")
class SkillFactoryTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("valid parameters should return skill with ACTIVE status")
        void validParametersShouldReturnSkill() {
            Optional<Skill> result = SkillFactory.create(
                    "perf-audit", "Check page load times", SkillCategory.PERFORMANCE);

            assertThat(result).isPresent();
            Skill skill = result.get();
            assertThat(skill.name()).isEqualTo("perf-audit");
            assertThat(skill.instructions()).isEqualTo("Check page load times");
            assertThat(skill.category()).isEqualTo(SkillCategory.PERFORMANCE);
            assertThat(skill.status()).isEqualTo(SkillStatus.ACTIVE);
            assertThat(skill.patterns()).isEmpty();
            assertThat(skill.sourceUrl()).isNull();
            assertThat(skill.sourceHash()).isNull();
            assertThat(skill.id()).isNotNull();
            assertThat(skill.createdAt()).isNotNull();
            assertThat(skill.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("null name should return empty")
        void nullNameShouldReturnEmpty() {
            assertThat(SkillFactory.create(null, "instructions", SkillCategory.TESTING)).isEmpty();
        }

        @Test
        @DisplayName("blank name should return empty")
        void blankNameShouldReturnEmpty() {
            assertThat(SkillFactory.create("  ", "instructions", SkillCategory.TESTING)).isEmpty();
        }

        @Test
        @DisplayName("null instructions should return empty")
        void nullInstructionsShouldReturnEmpty() {
            assertThat(SkillFactory.create("name", null, SkillCategory.TESTING)).isEmpty();
        }

        @Test
        @DisplayName("blank instructions should return empty")
        void blankInstructionsShouldReturnEmpty() {
            assertThat(SkillFactory.create("name", "  ", SkillCategory.TESTING)).isEmpty();
        }

        @Test
        @DisplayName("null category should return empty")
        void nullCategoryShouldReturnEmpty() {
            assertThat(SkillFactory.create("name", "instructions", null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromExternalSource()")
    class FromExternalSourceTests {

        @Test
        @DisplayName("should create skill with DRAFT status and source info")
        void shouldCreateSkillWithDraftStatus() {
            Optional<Skill> result = SkillFactory.fromExternalSource(
                    "external-skill", "External instructions", SkillCategory.SECURITY,
                    "https://example.com/skill", "sha256hash");

            assertThat(result).isPresent();
            Skill skill = result.get();
            assertThat(skill.status()).isEqualTo(SkillStatus.DRAFT);
            assertThat(skill.sourceUrl()).isEqualTo("https://example.com/skill");
            assertThat(skill.sourceHash()).isEqualTo("sha256hash");
        }

        @Test
        @DisplayName("null name should return empty")
        void nullNameShouldReturnEmpty() {
            assertThat(SkillFactory.fromExternalSource(
                    null, "instructions", SkillCategory.TESTING, "url", "hash")).isEmpty();
        }

        @Test
        @DisplayName("null instructions should return empty")
        void nullInstructionsShouldReturnEmpty() {
            assertThat(SkillFactory.fromExternalSource(
                    "name", null, SkillCategory.TESTING, "url", "hash")).isEmpty();
        }

        @Test
        @DisplayName("null category should return empty")
        void nullCategoryShouldReturnEmpty() {
            assertThat(SkillFactory.fromExternalSource(
                    "name", "instructions", null, "url", "hash")).isEmpty();
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should restore all fields")
        void shouldRestoreAllFields() {
            UUID id = UUID.randomUUID();
            Instant created = Instant.now().minusSeconds(3600);
            Instant updated = Instant.now();

            Skill skill = SkillFactory.reconstitute(
                    id, "security-scan", "Scan for XSS",
                    List.of("*.html", "*.js"), SkillCategory.SECURITY,
                    SkillStatus.ACTIVE, "https://source.com", "hash123",
                    created, updated);

            assertThat(skill.id().value()).isEqualTo(id);
            assertThat(skill.name()).isEqualTo("security-scan");
            assertThat(skill.instructions()).isEqualTo("Scan for XSS");
            assertThat(skill.patterns()).containsExactly("*.html", "*.js");
            assertThat(skill.category()).isEqualTo(SkillCategory.SECURITY);
            assertThat(skill.status()).isEqualTo(SkillStatus.ACTIVE);
            assertThat(skill.sourceUrl()).isEqualTo("https://source.com");
            assertThat(skill.sourceHash()).isEqualTo("hash123");
            assertThat(skill.createdAt()).isEqualTo(created);
            assertThat(skill.updatedAt()).isEqualTo(updated);
        }
    }
}
