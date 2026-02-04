package com.ai2qa.application.skill;

import com.ai2qa.application.persona.PersonaRegistry;
import com.ai2qa.domain.factory.PersonaDefinitionFactory;
import com.ai2qa.domain.factory.SkillFactory;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.model.persona.SkillReference;
import com.ai2qa.domain.model.skill.Skill;
import com.ai2qa.domain.model.skill.SkillCategory;
import com.ai2qa.domain.repository.PersonaRepository;
import com.ai2qa.domain.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the skill absorption pipeline.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Absorb skills from external GitHub sources (fetch → parse → save)</li>
 *   <li>Map skills to persona definitions by updating their skill references</li>
 *   <li>Run the full pipeline: absorb all configured skills, then map to personas</li>
 * </ul>
 */
@Service
public class SkillAbsorptionService {

    private static final Logger log = LoggerFactory.getLogger(SkillAbsorptionService.class);

    private final GitHubSkillFetcher fetcher;
    private final SkillRepository skillRepository;
    private final PersonaRepository personaRepository;
    private final PersonaRegistry personaRegistry;

    public SkillAbsorptionService(
            GitHubSkillFetcher fetcher,
            SkillRepository skillRepository,
            PersonaRepository personaRepository,
            PersonaRegistry personaRegistry) {
        this.fetcher = fetcher;
        this.skillRepository = skillRepository;
        this.personaRepository = personaRepository;
        this.personaRegistry = personaRegistry;
    }

    /**
     * Absorbs a single skill from a GitHub URL.
     *
     * <p>Pipeline: fetch → parse → create DRAFT skill → save.
     *
     * @param name     The skill name
     * @param url      The GitHub URL to fetch from
     * @param category The skill category
     * @return Optional containing the saved skill, or empty on failure
     */
    @Transactional
    public Optional<Skill> absorbFromGitHub(String name, String url, SkillCategory category) {
        return fetcher.fetch(url)
                .flatMap(fetchResult ->
                        SkillMdParser.parse(fetchResult.content())
                                .flatMap(parsed ->
                                        SkillFactory.fromExternalSource(
                                                name,
                                                parsed.markdownBody(),
                                                category,
                                                fetchResult.sourceUrl(),
                                                fetchResult.sourceHash()
                                        )
                                )
                                .map(skillRepository::save)
                );
    }

    /**
     * Maps skills to a persona by updating its skill reference list.
     *
     * <p>Reads the existing persona, builds a new skill reference list from
     * the provided mappings, reconstitutes with updated skills, and saves.
     *
     * @param personaName The persona name to update
     * @param mappings    The skill-to-priority mappings
     */
    @Transactional
    public void mapSkillsToPersona(String personaName, List<SkillMapping> mappings) {
        Optional<PersonaDefinition> personaOpt = personaRepository.findByName(personaName);
        if (personaOpt.isEmpty()) {
            log.warn("Persona '{}' not found, skipping skill mapping", personaName);
            return;
        }

        PersonaDefinition persona = personaOpt.get();
        List<SkillReference> skillRefs = new ArrayList<>();

        for (SkillMapping mapping : mappings) {
            Optional<Skill> skillOpt = skillRepository.findByName(mapping.skillName());
            if (skillOpt.isEmpty()) {
                log.warn("Skill '{}' not found, skipping for persona '{}'",
                        mapping.skillName(), personaName);
                continue;
            }

            Skill skill = skillOpt.get();
            skillRefs.add(new SkillReference(skill.id(), skill.name(), mapping.priority()));
        }

        PersonaDefinition updated = PersonaDefinitionFactory.reconstitute(
                persona.id().value(),
                persona.name(),
                persona.displayName(),
                persona.temperature(),
                persona.systemPrompt(),
                skillRefs,
                persona.source(),
                persona.active()
        );

        personaRepository.save(updated);
        log.info("Mapped {} skills to persona '{}'", skillRefs.size(), personaName);
    }

    /**
     * Runs the full absorption pipeline: fetch all skills, then map to personas.
     *
     * @param skills     The skill sources to absorb
     * @param personaMap The persona-to-skill mapping configuration
     * @return The absorption result with counts and errors
     */
    @Transactional
    public AbsorptionResult absorbAll(
            List<SkillSourceEntry> skills,
            Map<String, List<SkillMapping>> personaMap) {

        List<String> errors = new ArrayList<>();
        int skillsAbsorbed = 0;

        for (SkillSourceEntry entry : skills) {
            Optional<Skill> absorbed = absorbFromGitHub(entry.name(), entry.url(), entry.category());
            if (absorbed.isPresent()) {
                skillsAbsorbed++;
            } else {
                errors.add("Failed to absorb skill: " + entry.name());
            }
        }

        int personasUpdated = 0;
        for (Map.Entry<String, List<SkillMapping>> entry : personaMap.entrySet()) {
            mapSkillsToPersona(entry.getKey(), entry.getValue());
            personasUpdated++;
        }

        personaRegistry.invalidateCache();

        return new AbsorptionResult(skillsAbsorbed, personasUpdated, List.copyOf(errors));
    }

    /**
     * A skill-to-priority mapping entry.
     */
    public record SkillMapping(String skillName, int priority) {
    }

    /**
     * A skill source entry for the absorption pipeline.
     */
    public record SkillSourceEntry(String name, String url, SkillCategory category) {
    }

    /**
     * Result of the full absorption pipeline.
     */
    public record AbsorptionResult(int skillsAbsorbed, int personasUpdated, List<String> errors) {
    }
}
