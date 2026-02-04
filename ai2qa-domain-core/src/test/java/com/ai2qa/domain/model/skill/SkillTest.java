package com.ai2qa.domain.model.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Skill")
class SkillTest {

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("patterns list should be unmodifiable")
        void patternsListShouldBeUnmodifiable() {
            List<String> mutablePatterns = new ArrayList<>();
            mutablePatterns.add("*.test.js");

            Instant now = Instant.now();
            Skill skill = new Skill(
                    new SkillId(UUID.randomUUID()),
                    "test-skill",
                    "Test instructions",
                    mutablePatterns,
                    SkillCategory.TESTING,
                    SkillStatus.ACTIVE,
                    null,
                    null,
                    now,
                    now
            );

            // Modifying the original list should not affect the skill
            mutablePatterns.add("*.spec.js");
            assertThat(skill.patterns()).hasSize(1);

            // Returned list should be unmodifiable
            assertThatThrownBy(() -> skill.patterns().add("injected"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("null patterns list should default to empty")
        void nullPatternsListShouldDefaultToEmpty() {
            Instant now = Instant.now();
            Skill skill = new Skill(
                    new SkillId(UUID.randomUUID()),
                    "test-skill",
                    "Test instructions",
                    null,
                    SkillCategory.TESTING,
                    SkillStatus.ACTIVE,
                    null,
                    null,
                    now,
                    now
            );

            assertThat(skill.patterns()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record fields")
    class RecordFieldTests {

        @Test
        @DisplayName("should preserve all field values")
        void shouldPreserveAllFieldValues() {
            SkillId id = new SkillId(UUID.randomUUID());
            Instant created = Instant.now();
            Instant updated = Instant.now();

            Skill skill = new Skill(
                    id,
                    "perf-audit",
                    "Check page load times",
                    List.of("*.perf.js"),
                    SkillCategory.PERFORMANCE,
                    SkillStatus.ACTIVE,
                    "https://example.com/skill",
                    "abc123hash",
                    created,
                    updated
            );

            assertThat(skill.id()).isEqualTo(id);
            assertThat(skill.name()).isEqualTo("perf-audit");
            assertThat(skill.instructions()).isEqualTo("Check page load times");
            assertThat(skill.patterns()).containsExactly("*.perf.js");
            assertThat(skill.category()).isEqualTo(SkillCategory.PERFORMANCE);
            assertThat(skill.status()).isEqualTo(SkillStatus.ACTIVE);
            assertThat(skill.sourceUrl()).isEqualTo("https://example.com/skill");
            assertThat(skill.sourceHash()).isEqualTo("abc123hash");
            assertThat(skill.createdAt()).isEqualTo(created);
            assertThat(skill.updatedAt()).isEqualTo(updated);
        }
    }
}
