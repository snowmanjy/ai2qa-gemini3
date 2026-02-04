package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.model.persona.SkillReference;
import com.ai2qa.domain.model.skill.SkillId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PersonaDefinitionFactory")
class PersonaDefinitionFactoryTest {

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("valid parameters should return persona")
        void validParametersShouldReturnPersona() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "STANDARD", "The Strict Auditor", 0.2, "System prompt", PersonaSource.BUILTIN);

            assertThat(result).isPresent();
            PersonaDefinition persona = result.get();
            assertThat(persona.name()).isEqualTo("STANDARD");
            assertThat(persona.displayName()).isEqualTo("The Strict Auditor");
            assertThat(persona.temperature()).isEqualTo(0.2);
            assertThat(persona.systemPrompt()).isEqualTo("System prompt");
            assertThat(persona.source()).isEqualTo(PersonaSource.BUILTIN);
            assertThat(persona.active()).isTrue();
            assertThat(persona.skills()).isEmpty();
            assertThat(persona.id()).isNotNull();
        }

        @Test
        @DisplayName("null name should return empty")
        void nullNameShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    null, "Display", 0.2, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("blank name should return empty")
        void blankNameShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "   ", "Display", 0.2, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null display name should return empty")
        void nullDisplayNameShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", null, 0.2, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("blank display name should return empty")
        void blankDisplayNameShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "  ", 0.2, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("negative temperature should return empty")
        void negativeTemperatureShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", -0.1, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("temperature above 2.0 should return empty")
        void temperatureAboveTwoShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 2.1, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("temperature at boundary 0.0 should succeed")
        void temperatureAtZeroShouldSucceed() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 0.0, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("temperature at boundary 2.0 should succeed")
        void temperatureAtTwoShouldSucceed() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 2.0, "Prompt", PersonaSource.BUILTIN);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("null system prompt should return empty")
        void nullSystemPromptShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 0.2, null, PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("blank system prompt should return empty")
        void blankSystemPromptShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 0.2, "  ", PersonaSource.BUILTIN);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("null source should return empty")
        void nullSourceShouldReturnEmpty() {
            Optional<PersonaDefinition> result = PersonaDefinitionFactory.create(
                    "TEST", "Display", 0.2, "Prompt", null);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should restore all fields")
        void shouldRestoreAllFields() {
            UUID id = UUID.randomUUID();
            SkillReference skill = new SkillReference(new SkillId(UUID.randomUUID()), "skill-1", 1);

            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    id, "CHAOS", "The Gremlin", 0.6, "Chaos prompt",
                    List.of(skill), PersonaSource.BUILTIN, true);

            assertThat(persona.id().value()).isEqualTo(id);
            assertThat(persona.name()).isEqualTo("CHAOS");
            assertThat(persona.displayName()).isEqualTo("The Gremlin");
            assertThat(persona.temperature()).isEqualTo(0.6);
            assertThat(persona.systemPrompt()).isEqualTo("Chaos prompt");
            assertThat(persona.skills()).hasSize(1);
            assertThat(persona.source()).isEqualTo(PersonaSource.BUILTIN);
            assertThat(persona.active()).isTrue();
        }

        @Test
        @DisplayName("should handle inactive persona")
        void shouldHandleInactivePersona() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "DEPRECATED", "Old", 0.2, "Prompt",
                    List.of(), PersonaSource.CUSTOM, false);

            assertThat(persona.active()).isFalse();
        }
    }
}
