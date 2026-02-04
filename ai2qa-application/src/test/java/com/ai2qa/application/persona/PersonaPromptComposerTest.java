package com.ai2qa.application.persona;

import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.model.persona.SkillReference;
import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.model.skill.SkillId;
import com.ai2qa.domain.model.skill.SkillStatus;
import com.ai2qa.domain.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("PersonaPromptComposer")
class PersonaPromptComposerTest {

    private SkillRepository skillRepository;
    private PersonaPromptComposer composer;

    @BeforeEach
    void setUp() {
        skillRepository = mock(SkillRepository.class);
        composer = new PersonaPromptComposer(skillRepository);
    }

    @Nested
    @DisplayName("compose()")
    class ComposeTests {

        @Test
        @DisplayName("persona with no skills returns base prompt")
        void noSkillsReturnsBasePrompt() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "Auditor", 0.2,
                    "Base system prompt", List.of(), PersonaSource.BUILTIN, true);

            String result = composer.compose(persona, null);

            assertThat(result).isEqualTo("Base system prompt");
        }

        @Test
        @DisplayName("persona with skills appends skill instructions in priority order")
        void withSkillsAppendsInPriorityOrder() {
            SkillId skillId1 = new SkillId(UUID.randomUUID());
            SkillId skillId2 = new SkillId(UUID.randomUUID());

            SkillReference ref1 = new SkillReference(skillId1, "perf-check", 2);
            SkillReference ref2 = new SkillReference(skillId2, "a11y-check", 1);

            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CUSTOM", "Custom", 0.3,
                    "Base prompt", List.of(ref1, ref2), PersonaSource.CUSTOM, true);

            Instant now = Instant.now();
            Skill skill1 = new Skill(skillId1, "perf-check", "Check performance",
                    List.of(), SkillCategory.PERFORMANCE, SkillStatus.ACTIVE, null, null, now, now);
            Skill skill2 = new Skill(skillId2, "a11y-check", "Check accessibility",
                    List.of(), SkillCategory.ACCESSIBILITY, SkillStatus.ACTIVE, null, null, now, now);

            when(skillRepository.findById(skillId1)).thenReturn(Optional.of(skill1));
            when(skillRepository.findById(skillId2)).thenReturn(Optional.of(skill2));

            String result = composer.compose(persona, null);

            assertThat(result).contains("Base prompt");
            assertThat(result).contains("[ADDITIONAL SKILLS]");
            assertThat(result).contains("Check accessibility");
            assertThat(result).contains("Check performance");

            // a11y (priority 1) should appear before perf (priority 2)
            int a11yIndex = result.indexOf("Check accessibility");
            int perfIndex = result.indexOf("Check performance");
            assertThat(a11yIndex).isLessThan(perfIndex);
        }

        @Test
        @DisplayName("compose with memory context appends it")
        void withMemoryContextAppendsIt() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "Auditor", 0.2,
                    "Base prompt", List.of(), PersonaSource.BUILTIN, true);

            String memoryContext = "\n\n[GLOBAL HIVE MIND ACTIVATED]\n- framework:react: Use data-testid\n";

            String result = composer.compose(persona, memoryContext);

            assertThat(result).contains("Base prompt");
            assertThat(result).contains("[GLOBAL HIVE MIND ACTIVATED]");
            assertThat(result).contains("data-testid");
        }

        @Test
        @DisplayName("compose with skills and memory includes both")
        void withSkillsAndMemoryIncludesBoth() {
            SkillId skillId = new SkillId(UUID.randomUUID());
            SkillReference ref = new SkillReference(skillId, "perf", 1);

            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CUSTOM", "Custom", 0.3,
                    "Base prompt", List.of(ref), PersonaSource.CUSTOM, true);

            Instant now = Instant.now();
            Skill skill = new Skill(skillId, "perf", "Check perf",
                    List.of(), SkillCategory.PERFORMANCE, SkillStatus.ACTIVE, null, null, now, now);
            when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));

            String memoryContext = "\n\n[HIVE MIND]\n- hint: wait for hydration\n";

            String result = composer.compose(persona, memoryContext);

            assertThat(result).contains("Base prompt");
            assertThat(result).contains("[ADDITIONAL SKILLS]");
            assertThat(result).contains("Check perf");
            assertThat(result).contains("[HIVE MIND]");
            assertThat(result).contains("wait for hydration");
        }

        @Test
        @DisplayName("blank memory context is not appended")
        void blankMemoryContextNotAppended() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "Auditor", 0.2,
                    "Base prompt", List.of(), PersonaSource.BUILTIN, true);

            String result = composer.compose(persona, "   ");

            assertThat(result).isEqualTo("Base prompt");
        }

        @Test
        @DisplayName("skill not found in repository is silently skipped")
        void missingSkillIsSilentlySkipped() {
            SkillId skillId = new SkillId(UUID.randomUUID());
            SkillReference ref = new SkillReference(skillId, "missing-skill", 1);

            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CUSTOM", "Custom", 0.3,
                    "Base prompt", List.of(ref), PersonaSource.CUSTOM, true);

            when(skillRepository.findById(skillId)).thenReturn(Optional.empty());

            String result = composer.compose(persona, null);

            assertThat(result).contains("Base prompt");
            assertThat(result).contains("[ADDITIONAL SKILLS]");
            assertThat(result).doesNotContain("missing-skill");
        }
    }
}
