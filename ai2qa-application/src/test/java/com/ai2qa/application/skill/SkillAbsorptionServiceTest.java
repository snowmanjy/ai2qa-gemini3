package com.ai2qa.application.skill;

import com.ai2qa.application.persona.PersonaRegistry;
import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.factory.SkillFactory;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.PersonaSource;
import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.repository.PersonaRepository;
import com.ai2qa.domain.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("SkillAbsorptionService")
class SkillAbsorptionServiceTest {

    private GitHubSkillFetcher fetcher;
    private SkillRepository skillRepository;
    private PersonaRepository personaRepository;
    private PersonaRegistry personaRegistry;
    private SkillAbsorptionService service;

    @BeforeEach
    void setUp() {
        fetcher = mock(GitHubSkillFetcher.class);
        skillRepository = mock(SkillRepository.class);
        personaRepository = mock(PersonaRepository.class);
        personaRegistry = mock(PersonaRegistry.class);
        service = new SkillAbsorptionService(fetcher, skillRepository, personaRepository, personaRegistry);
    }

    @Nested
    @DisplayName("absorbFromGitHub()")
    class AbsorbFromGitHubTests {

        @Test
        @DisplayName("happy path fetches, parses, and saves skill with DRAFT status")
        void happyPathSavesSkill() {
            String skillMd = """
                    ---
                    name: webapp-testing
                    description: Browser testing patterns
                    ---
                    # Webapp Testing

                    Use Playwright for DOM verification.
                    """;

            GitHubSkillFetcher.FetchResult fetchResult =
                    new GitHubSkillFetcher.FetchResult(skillMd, "https://raw.example.com/SKILL.md", "abc123");

            when(fetcher.fetch("https://github.com/org/repo/blob/main/SKILL.md"))
                    .thenReturn(Optional.of(fetchResult));

            when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));

            Optional<Skill> result = service.absorbFromGitHub(
                    "webapp-testing",
                    "https://github.com/org/repo/blob/main/SKILL.md",
                    SkillCategory.TESTING);

            assertThat(result).isPresent();
            verify(skillRepository).save(argThat(skill ->
                    skill.name().equals("webapp-testing") &&
                    skill.sourceUrl().equals("https://raw.example.com/SKILL.md") &&
                    skill.sourceHash().equals("abc123")
            ));
        }

        @Test
        @DisplayName("fetch failure returns empty")
        void fetchFailureReturnsEmpty() {
            when(fetcher.fetch(any())).thenReturn(Optional.empty());

            Optional<Skill> result = service.absorbFromGitHub(
                    "test", "https://example.com", SkillCategory.TESTING);

            assertThat(result).isEmpty();
            verify(skillRepository, never()).save(any());
        }

        @Test
        @DisplayName("parse failure returns empty")
        void parseFailureReturnsEmpty() {
            GitHubSkillFetcher.FetchResult fetchResult =
                    new GitHubSkillFetcher.FetchResult("not valid yaml", "url", "hash");

            when(fetcher.fetch(any())).thenReturn(Optional.of(fetchResult));

            Optional<Skill> result = service.absorbFromGitHub(
                    "test", "https://example.com", SkillCategory.TESTING);

            assertThat(result).isEmpty();
            verify(skillRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("mapSkillsToPersona()")
    class MapSkillsToPersonaTests {

        @Test
        @DisplayName("maps skills correctly to existing persona")
        void mapsSkillsCorrectly() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "Auditor", 0.2,
                    "System prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.of(persona));

            Skill skill = SkillFactory.create("webapp-testing", "Instructions", SkillCategory.TESTING).get();
            when(skillRepository.findByName("webapp-testing")).thenReturn(Optional.of(skill));

            when(personaRepository.save(any(PersonaDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.mapSkillsToPersona("STANDARD",
                    List.of(new SkillAbsorptionService.SkillMapping("webapp-testing", 1)));

            verify(personaRepository).save(argThat(saved ->
                    saved.name().equals("STANDARD") &&
                    saved.skills().size() == 1 &&
                    saved.skills().get(0).skillName().equals("webapp-testing") &&
                    saved.skills().get(0).priority() == 1
            ));
        }

        @Test
        @DisplayName("persona not found logs warning and does not save")
        void personaNotFoundSkips() {
            when(personaRepository.findByName("UNKNOWN")).thenReturn(Optional.empty());

            service.mapSkillsToPersona("UNKNOWN",
                    List.of(new SkillAbsorptionService.SkillMapping("webapp-testing", 1)));

            verify(personaRepository, never()).save(any());
        }

        @Test
        @DisplayName("skill not found skips that mapping but saves others")
        void skillNotFoundSkipsMapping() {
            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "CHAOS", "Gremlin", 0.6,
                    "Chaos prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("CHAOS")).thenReturn(Optional.of(persona));

            Skill existingSkill = SkillFactory.create("web-fuzzing", "Instructions", SkillCategory.SECURITY).get();
            when(skillRepository.findByName("web-fuzzing")).thenReturn(Optional.of(existingSkill));
            when(skillRepository.findByName("nonexistent")).thenReturn(Optional.empty());

            when(personaRepository.save(any(PersonaDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.mapSkillsToPersona("CHAOS", List.of(
                    new SkillAbsorptionService.SkillMapping("web-fuzzing", 1),
                    new SkillAbsorptionService.SkillMapping("nonexistent", 2)
            ));

            verify(personaRepository).save(argThat(saved ->
                    saved.skills().size() == 1 &&
                    saved.skills().get(0).skillName().equals("web-fuzzing")
            ));
        }
    }

    @Nested
    @DisplayName("absorbAll()")
    class AbsorbAllTests {

        @Test
        @DisplayName("orchestrates full pipeline: absorb skills then map to personas")
        void orchestratesFullPipeline() {
            String skillMd = """
                    ---
                    name: test-skill
                    description: Test
                    ---
                    # Test

                    Instructions here.
                    """;

            GitHubSkillFetcher.FetchResult fetchResult =
                    new GitHubSkillFetcher.FetchResult(skillMd, "https://raw.example.com/SKILL.md", "hash");
            when(fetcher.fetch(any())).thenReturn(Optional.of(fetchResult));
            when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));

            PersonaDefinition persona = PersonaDefinitionFactory.reconstitute(
                    UUID.randomUUID(), "STANDARD", "Auditor", 0.2,
                    "Prompt", List.of(), PersonaSource.BUILTIN, true);
            when(personaRepository.findByName("STANDARD")).thenReturn(Optional.of(persona));

            Skill savedSkill = SkillFactory.create("test-skill", "Instructions here.", SkillCategory.TESTING).get();
            when(skillRepository.findByName("test-skill")).thenReturn(Optional.of(savedSkill));
            when(personaRepository.save(any(PersonaDefinition.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<SkillAbsorptionService.SkillSourceEntry> skills = List.of(
                    new SkillAbsorptionService.SkillSourceEntry(
                            "test-skill", "https://github.com/org/repo/blob/main/SKILL.md",
                            SkillCategory.TESTING)
            );

            Map<String, List<SkillAbsorptionService.SkillMapping>> personaMap = Map.of(
                    "STANDARD", List.of(new SkillAbsorptionService.SkillMapping("test-skill", 1))
            );

            SkillAbsorptionService.AbsorptionResult result = service.absorbAll(skills, personaMap);

            assertThat(result.skillsAbsorbed()).isEqualTo(1);
            assertThat(result.personasUpdated()).isEqualTo(1);
            assertThat(result.errors()).isEmpty();
            verify(personaRegistry).invalidateCache();
        }

        @Test
        @DisplayName("records errors for failed skill absorptions")
        void recordsErrorsForFailures() {
            when(fetcher.fetch(any())).thenReturn(Optional.empty());

            List<SkillAbsorptionService.SkillSourceEntry> skills = List.of(
                    new SkillAbsorptionService.SkillSourceEntry(
                            "failing-skill", "https://example.com/bad", SkillCategory.TESTING)
            );

            SkillAbsorptionService.AbsorptionResult result = service.absorbAll(skills, Map.of());

            assertThat(result.skillsAbsorbed()).isZero();
            assertThat(result.errors()).containsExactly("Failed to absorb skill: failing-skill");
            verify(personaRegistry).invalidateCache();
        }
    }
}
