package com.ai2qa.application.persona;

import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("PersonaRegistry")
class PersonaRegistryTest {

    private PersonaRepository personaRepository;
    private PersonaRegistry registry;

    @BeforeEach
    void setUp() {
        personaRepository = mock(PersonaRepository.class);
        registry = new PersonaRegistry(personaRepository);
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("STANDARD from DB returns correct PersonaDefinition")
        void standardFromDbReturnsCorrectPersona() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "The Auditor", 0.2,
                    "System prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.of(dbPersona));

            PersonaDefinition result = registry.resolve("STANDARD");

            assertThat(result.name()).isEqualTo("STANDARD");
            assertThat(result.temperature()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("CHAOS from DB returns correct PersonaDefinition")
        void chaosFromDbReturnsCorrectPersona() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CHAOS", "The Gremlin", 0.6,
                    "Chaos prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("CHAOS")).thenReturn(Optional.of(dbPersona));

            PersonaDefinition result = registry.resolve("CHAOS");

            assertThat(result.name()).isEqualTo("CHAOS");
            assertThat(result.temperature()).isEqualTo(0.6);
        }

        @Test
        @DisplayName("HACKER from DB returns correct PersonaDefinition")
        void hackerFromDbReturnsCorrectPersona() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "HACKER", "The Red Teamer", 0.4,
                    "Hacker prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("HACKER")).thenReturn(Optional.of(dbPersona));

            PersonaDefinition result = registry.resolve("HACKER");

            assertThat(result.name()).isEqualTo("HACKER");
            assertThat(result.temperature()).isEqualTo(0.4);
        }

        @Test
        @DisplayName("PERFORMANCE_HAWK resolves from DB (no enum counterpart)")
        void performanceHawkResolvesFromDb() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "PERFORMANCE_HAWK", "The Performance Hawk", 0.3,
                    "Performance prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("PERFORMANCE_HAWK")).thenReturn(Optional.of(dbPersona));

            PersonaDefinition result = registry.resolve("PERFORMANCE_HAWK");

            assertThat(result.name()).isEqualTo("PERFORMANCE_HAWK");
            assertThat(result.displayName()).isEqualTo("The Performance Hawk");
            assertThat(result.temperature()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("null name returns STANDARD default")
        void nullNameReturnsStandardDefault() {
            // DB lookup for STANDARD should fall through to enum
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.empty());

            PersonaDefinition result = registry.resolve(null);

            assertThat(result.name()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("blank name returns STANDARD default")
        void blankNameReturnsStandardDefault() {
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.empty());

            PersonaDefinition result = registry.resolve("   ");

            assertThat(result.name()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("unknown name returns STANDARD default")
        void unknownNameReturnsStandardDefault() {
            when(personaRepository.findByName("UNKNOWN")).thenReturn(Optional.empty());
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.empty());

            PersonaDefinition result = registry.resolve("UNKNOWN");

            assertThat(result.name()).isEqualTo("STANDARD");
        }

        @Test
        @DisplayName("resolve is case-insensitive")
        void resolveIsCaseInsensitive() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CHAOS", "The Gremlin", 0.6,
                    "Chaos prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("CHAOS")).thenReturn(Optional.of(dbPersona));

            PersonaDefinition result = registry.resolve("chaos");

            assertThat(result.name()).isEqualTo("CHAOS");
        }

        @Test
        @DisplayName("cache hit avoids DB call on second resolve")
        void cacheHitAvoidsDbCall() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "The Auditor", 0.2,
                    "Prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.of(dbPersona));

            // First call: goes to DB
            registry.resolve("STANDARD");
            // Second call: should use cache
            registry.resolve("STANDARD");

            verify(personaRepository, times(1)).findByName("STANDARD");
        }

        @Test
        @DisplayName("enum fallback works when DB returns empty")
        void enumFallbackWorksWhenDbReturnsEmpty() {
            when(personaRepository.findByName("CHAOS")).thenReturn(Optional.empty());

            PersonaDefinition result = registry.resolve("CHAOS");

            assertThat(result.name()).isEqualTo("CHAOS");
            assertThat(result.temperature()).isEqualTo(0.6);
            assertThat(result.systemPrompt()).contains("Gremlin");
        }
    }

    @Nested
    @DisplayName("invalidateCache()")
    class CacheInvalidationTests {

        @Test
        @DisplayName("invalidateCache forces DB re-read")
        void invalidateCacheForcesDbReRead() {
            PersonaDefinition dbPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "The Auditor", 0.2,
                    "Prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.of(dbPersona));

            // First resolve: hits DB
            registry.resolve("STANDARD");
            verify(personaRepository, times(1)).findByName("STANDARD");

            // Invalidate cache
            registry.invalidateCache();

            // Second resolve: hits DB again
            registry.resolve("STANDARD");
            verify(personaRepository, times(2)).findByName("STANDARD");
        }
    }

    @Nested
    @DisplayName("listActive()")
    class ListActiveTests {

        @Test
        @DisplayName("returns all active personas from DB supplemented by enum")
        void returnsAllActivePersonas() {
            PersonaDefinition hawkPersona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "PERFORMANCE_HAWK", "The Performance Hawk", 0.3,
                    "Perf prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findAllActive()).thenReturn(List.of(hawkPersona));

            List<PersonaDefinition> result = registry.listActive();

            // Should have PERFORMANCE_HAWK from DB + STANDARD, CHAOS, HACKER from enum fallback
            assertThat(result).hasSizeGreaterThanOrEqualTo(4);
            assertThat(result).anyMatch(p -> "PERFORMANCE_HAWK".equals(p.name()));
            assertThat(result).anyMatch(p -> "STANDARD".equals(p.name()));
            assertThat(result).anyMatch(p -> "CHAOS".equals(p.name()));
            assertThat(result).anyMatch(p -> "HACKER".equals(p.name()));
        }

        @Test
        @DisplayName("does not duplicate personas that exist in both DB and enum")
        void doesNotDuplicatePersonas() {
            PersonaDefinition standardFromDb = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "The Auditor", 0.2,
                    "Prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findAllActive()).thenReturn(List.of(standardFromDb));

            List<PersonaDefinition> result = registry.listActive();

            long standardCount = result.stream()
                    .filter(p -> "STANDARD".equalsIgnoreCase(p.name()))
                    .count();
            assertThat(standardCount).isEqualTo(1);
        }
    }
}
