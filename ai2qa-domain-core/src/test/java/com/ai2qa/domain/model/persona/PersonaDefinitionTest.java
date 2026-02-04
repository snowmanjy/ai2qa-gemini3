package com.ai2qa.domain.model.persona;

import com.ai2qa.domain.model.skill.SkillId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PersonaDefinition")
class PersonaDefinitionTest {

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("skills list should be unmodifiable")
        void skillsListShouldBeUnmodifiable() {
            List<SkillReference> mutableSkills = new ArrayList<>();
            mutableSkills.add(new SkillReference(new SkillId(UUID.randomUUID()), "test-skill", 1));

            PersonaDefinition persona = new PersonaDefinition(
                    new PersonaId(UUID.randomUUID()),
                    "TEST",
                    "Test Persona",
                    0.5,
                    "System prompt",
                    mutableSkills,
                    PersonaSource.BUILTIN,
                    true
            );

            // Modifying the original list should not affect the persona
            mutableSkills.add(new SkillReference(new SkillId(UUID.randomUUID()), "another-skill", 2));
            assertThat(persona.skills()).hasSize(1);

            // Returned list should be unmodifiable
            assertThatThrownBy(() -> persona.skills().add(
                    new SkillReference(new SkillId(UUID.randomUUID()), "injected", 3)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("null skills list should default to empty")
        void nullSkillsListShouldDefaultToEmpty() {
            PersonaDefinition persona = new PersonaDefinition(
                    new PersonaId(UUID.randomUUID()),
                    "TEST",
                    "Test Persona",
                    0.5,
                    "System prompt",
                    null,
                    PersonaSource.BUILTIN,
                    true
            );

            assertThat(persona.skills()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record fields")
    class RecordFieldTests {

        @Test
        @DisplayName("should preserve all field values")
        void shouldPreserveAllFieldValues() {
            PersonaId id = new PersonaId(UUID.randomUUID());
            SkillReference skill = new SkillReference(new SkillId(UUID.randomUUID()), "skill-1", 1);

            PersonaDefinition persona = new PersonaDefinition(
                    id,
                    "STANDARD",
                    "The Strict Auditor",
                    0.2,
                    "You are an auditor",
                    List.of(skill),
                    PersonaSource.BUILTIN,
                    true
            );

            assertThat(persona.id()).isEqualTo(id);
            assertThat(persona.name()).isEqualTo("STANDARD");
            assertThat(persona.displayName()).isEqualTo("The Strict Auditor");
            assertThat(persona.temperature()).isEqualTo(0.2);
            assertThat(persona.systemPrompt()).isEqualTo("You are an auditor");
            assertThat(persona.skills()).hasSize(1);
            assertThat(persona.source()).isEqualTo(PersonaSource.BUILTIN);
            assertThat(persona.active()).isTrue();
        }
    }
}
