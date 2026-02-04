package com.ai2qa.domain.model;

import com.ai2qa.domain.SystemConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TestPersona enum including temperature settings.
 */
class TestPersonaTest {

    @Nested
    @DisplayName("Persona Inventory")
    class PersonaInventoryTests {

        @Test
        @DisplayName("Should have exactly EXPECTED_PERSONA_COUNT personas")
        void shouldHaveExpectedPersonaCount() {
            assertThat(TestPersona.values())
                    .as("Persona count should match SystemConstants.EXPECTED_PERSONA_COUNT. " +
                        "If adding a new persona, update SystemConstants.EXPECTED_PERSONA_COUNT first, " +
                        "then add the enum value to TestPersona.")
                    .hasSize(SystemConstants.EXPECTED_PERSONA_COUNT);
        }

        @Test
        @DisplayName("Should have all expected personas: STANDARD, CHAOS, HACKER, PERFORMANCE_HAWK")
        void shouldHaveAllExpectedPersonas() {
            assertThat(TestPersona.values())
                    .containsExactlyInAnyOrder(
                            TestPersona.STANDARD,
                            TestPersona.CHAOS,
                            TestPersona.HACKER,
                            TestPersona.PERFORMANCE_HAWK
                    );
        }

        @Test
        @DisplayName("All personas should have display names")
        void allPersonas_shouldHaveDisplayNames() {
            for (TestPersona persona : TestPersona.values()) {
                assertThat(persona.getDisplayName())
                        .as("Display name for %s", persona)
                        .isNotBlank();
            }
        }
    }

    @Nested
    @DisplayName("Temperature Settings")
    class TemperatureTests {

        @Test
        @DisplayName("STANDARD persona should have low temperature (0.2) for deterministic behavior")
        void standard_shouldHaveLowTemperature() {
            assertThat(TestPersona.STANDARD.getTemperature()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("CHAOS persona should have medium-high temperature (0.6) for chaotic but structured behavior")
        void chaos_shouldHaveMediumHighTemperature() {
            assertThat(TestPersona.CHAOS.getTemperature()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("HACKER persona should have medium temperature (0.4) for varied attack vectors")
        void hacker_shouldHaveMediumTemperature() {
            assertThat(TestPersona.HACKER.getTemperature()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("All temperatures should be within valid range (0.0 to 1.0)")
        void allTemperatures_shouldBeInValidRange() {
            for (TestPersona persona : TestPersona.values()) {
                assertThat(persona.getTemperature())
                        .as("Temperature for %s", persona)
                        .isBetween(0.0, 1.0);
            }
        }

        @Test
        @DisplayName("CHAOS should have higher temperature than STANDARD")
        void chaos_shouldHaveHigherTemperatureThanStandard() {
            assertThat(TestPersona.CHAOS.getTemperature())
                    .isGreaterThan(TestPersona.STANDARD.getTemperature());
        }

        @Test
        @DisplayName("HACKER should have temperature between STANDARD and CHAOS")
        void hacker_shouldHaveTemperatureBetweenStandardAndChaos() {
            assertThat(TestPersona.HACKER.getTemperature())
                    .isGreaterThan(TestPersona.STANDARD.getTemperature())
                    .isLessThan(TestPersona.CHAOS.getTemperature());
        }

        @Test
        @DisplayName("PERFORMANCE_HAWK should have low temperature (0.3) for data-driven analysis")
        void performanceHawk_shouldHaveLowTemperature() {
            assertThat(TestPersona.PERFORMANCE_HAWK.getTemperature()).isEqualTo(0.3);
        }
    }

    @Nested
    @DisplayName("System Prompt")
    class SystemPromptTests {

        @Test
        @DisplayName("All personas should have non-empty system prompts")
        void allPersonas_shouldHaveSystemPrompts() {
            for (TestPersona persona : TestPersona.values()) {
                assertThat(persona.getSystemPrompt())
                        .as("System prompt for %s", persona)
                        .isNotBlank();
            }
        }

        @Test
        @DisplayName("STANDARD should have 'Auditor' in system prompt")
        void standard_shouldHaveAuditorPrompt() {
            assertThat(TestPersona.STANDARD.getSystemPrompt())
                    .contains("Auditor");
        }

        @Test
        @DisplayName("CHAOS should have 'Gremlin' in system prompt")
        void chaos_shouldHaveGremlinPrompt() {
            assertThat(TestPersona.CHAOS.getSystemPrompt())
                    .contains("Gremlin");
        }

        @Test
        @DisplayName("HACKER should have 'Red Teamer' in system prompt")
        void hacker_shouldHaveRedTeamerPrompt() {
            assertThat(TestPersona.HACKER.getSystemPrompt())
                    .contains("Red Teamer");
        }

        @Test
        @DisplayName("PERFORMANCE_HAWK should have 'Performance Hawk' in system prompt")
        void performanceHawk_shouldHavePerformanceHawkPrompt() {
            assertThat(TestPersona.PERFORMANCE_HAWK.getSystemPrompt())
                    .contains("Performance Hawk");
        }

        @Test
        @DisplayName("PERFORMANCE_HAWK should mention Core Web Vitals in system prompt")
        void performanceHawk_shouldMentionCoreWebVitals() {
            assertThat(TestPersona.PERFORMANCE_HAWK.getSystemPrompt().toUpperCase())
                    .contains("CORE WEB VITALS");
        }
    }

    @Nested
    @DisplayName("Default Persona")
    class DefaultPersonaTests {

        @Test
        @DisplayName("Default persona should be STANDARD")
        void defaultPersona_shouldBeStandard() {
            assertThat(TestPersona.defaultPersona()).isEqualTo(TestPersona.STANDARD);
        }

        @Test
        @DisplayName("Default persona should have deterministic temperature")
        void defaultPersona_shouldHaveDeterministicTemperature() {
            assertThat(TestPersona.defaultPersona().getTemperature())
                    .isLessThanOrEqualTo(0.3);
        }
    }
}
