package com.ai2qa.application.planner;

import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.application.memory.AgentMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plans action steps to achieve testing goals.
 *
 * <p>Uses AI to generate intelligent test plans based on goals, DOM state, and persona.
 */
@Component
public class StepPlanner {

    private static final Logger log = LoggerFactory.getLogger(StepPlanner.class);

    private final GeminiPlannerClient geminiClient;
    private final AgentMemoryService memoryService;

    public StepPlanner(GeminiPlannerClient geminiClient, AgentMemoryService memoryService) {
        this.geminiClient = geminiClient;
        this.memoryService = memoryService;
    }

    /**
     * Builds the Global Hive Mind context string for prompt injection.
     */
    private String buildMemoryContext() {
        try {
            Map<String, String> memory = memoryService.loadMemoryMap();
            if (memory.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n[GLOBAL HIVE MIND ACTIVATED]\n");
            sb.append("Knowledge Base loaded. Apply these specific rules if you detect the matching context:\n");
            
            memory.forEach((tag, wisdom) -> {
                sb.append(String.format("- 🧠 [%s]: %s\n", tag, wisdom));
            });

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to load memory context: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Creates an initial test plan based on target URL and goals with default STANDARD persona.
     *
     * @param targetUrl The URL to test
     * @param goals     List of test goals
     * @return List of action steps to execute
     */
    public List<ActionStep> createPlan(String targetUrl, List<String> goals) {
        return createPlan(targetUrl, goals, TestPersona.STANDARD);
    }

    /**
     * Creates an initial test plan based on target URL, goals, and persona.
     *
     * @param targetUrl The URL to test
     * @param goals     List of test goals
     * @param persona   The test persona to use
     * @return List of action steps to execute
     */
    public List<ActionStep> createPlan(String targetUrl, List<String> goals, TestPersona persona) {
        TestPersona effectivePersona = persona != null ? persona : TestPersona.STANDARD;

        // When no goals are provided, generate a default exploratory goal based on persona
        List<String> effectiveGoals = (goals == null || goals.isEmpty())
                ? List.of(buildDefaultGoal(targetUrl, effectivePersona))
                : goals;

        log.info("Creating plan for {} with {} goals using {} persona (original goals: {})",
                targetUrl, effectiveGoals.size(), effectivePersona, goals != null ? goals.size() : 0);

        String memoryContext = buildMemoryContext();
        List<ActionStep> plan = new ArrayList<>();

        // Always start with navigation
        plan.add(ActionStepFactory.navigate(targetUrl));

        // Use AI to generate steps for each goal with persona and memory context
        for (String goal : effectiveGoals) {
            List<ActionStep> goalSteps = geminiClient.planGoal(goal, targetUrl, effectivePersona, memoryContext);
            plan.addAll(goalSteps);
        }

        log.info("Created plan with {} steps", plan.size());
        return plan;
    }

    /**
     * Generates steps to achieve a single goal given current DOM state with default persona.
     *
     * @param goal     The goal to achieve
     * @param snapshot Current DOM snapshot
     * @return List of action steps
     */
    public List<ActionStep> planForGoal(String goal, DomSnapshot snapshot) {
        return planForGoal(goal, snapshot, TestPersona.STANDARD);
    }

    /**
     * Generates steps to achieve a single goal given current DOM state and persona.
     *
     * @param goal     The goal to achieve
     * @param snapshot Current DOM snapshot
     * @param persona  The test persona to use
     * @return List of action steps
     */
    public List<ActionStep> planForGoal(String goal, DomSnapshot snapshot, TestPersona persona) {
        TestPersona effectivePersona = persona != null ? persona : TestPersona.STANDARD;
        log.debug("Planning steps for goal: {} with {} persona", goal, effectivePersona);
        String memoryContext = buildMemoryContext();
        return geminiClient.planGoalWithContext(goal, snapshot, effectivePersona, memoryContext);
    }

    /**
     * Finds a selector for an element described in natural language.
     *
     * @param elementDescription Natural language description (e.g., "login button")
     * @param snapshot          Current DOM snapshot
     * @return Optional containing the selector if found
     */
    public Optional<String> findSelector(String elementDescription, DomSnapshot snapshot) {
        log.debug("Finding selector for: {}", elementDescription);
        return geminiClient.findSelector(elementDescription, snapshot);
    }

    /**
     * Generates repair steps when an action fails with default persona.
     *
     * @param failedStep The step that failed
     * @param error      Error message
     * @param snapshot   Current DOM snapshot
     * @return List of repair steps
     */
    public List<ActionStep> createRepairPlan(
            ActionStep failedStep,
            String error,
            DomSnapshot snapshot
    ) {
        return createRepairPlan(failedStep, error, snapshot, TestPersona.STANDARD);
    }

    /**
     * Generates repair steps when an action fails with persona context.
     *
     * @param failedStep The step that failed
     * @param error      Error message
     * @param snapshot   Current DOM snapshot
     * @param persona    The test persona to use
     * @return List of repair steps
     */
    public List<ActionStep> createRepairPlan(
            ActionStep failedStep,
            String error,
            DomSnapshot snapshot,
            TestPersona persona
    ) {
        TestPersona effectivePersona = persona != null ? persona : TestPersona.STANDARD;
        log.info("Creating repair plan for failed step: {} with {} persona", failedStep.action(), effectivePersona);
        String memoryContext = buildMemoryContext();
        return geminiClient.planRepair(failedStep, error, snapshot, effectivePersona, memoryContext);
    }

    /**
     * Builds a default exploratory goal when no explicit goals are provided.
     *
     * <p>Each persona gets a goal tailored to its specialty, ensuring the AI
     * generates meaningful test steps even when the user only provides a URL.
     */
    private String buildDefaultGoal(String targetUrl, TestPersona persona) {
        String goal = switch (persona) {
            case PERFORMANCE_HAWK -> "Thoroughly explore the website and measure performance metrics. " +
                    "Navigate through main pages, interact with key UI elements, and capture Core Web Vitals " +
                    "after each navigation and interaction.";
            case CHAOS -> "Explore the website chaotically - try unusual inputs, rapid interactions, " +
                    "edge cases, and unexpected user flows to find stability issues.";
            case HACKER -> "Perform a security assessment of the website - check for common vulnerabilities " +
                    "like XSS, injection points, exposed sensitive data, and missing security headers.";
            case STANDARD -> "Thoroughly explore and test the website - navigate through all main sections, " +
                    "interact with forms and buttons, verify links work, and check overall functionality.";
        };
        log.info("No goals provided - using default {} goal for {}", persona, targetUrl);
        return goal;
    }

    /**
     * Builds a default exploratory goal for PersonaDefinition when no explicit goals are provided.
     */
    private String buildDefaultGoalForPersonaDefinition(String targetUrl, PersonaDefinition persona) {
        String goal = "Thoroughly explore and test the website at " + targetUrl +
                " - navigate through all main sections, interact with key UI elements, " +
                "and perform testing according to your persona specialty.";
        log.info("No goals provided - using default goal for PersonaDefinition {} at {}", persona.name(), targetUrl);
        return goal;
    }

    // ========== PersonaDefinition overloads ==========

    /**
     * Creates an initial test plan using a PersonaDefinition.
     *
     * @param targetUrl The URL to test
     * @param goals     List of test goals
     * @param persona   The persona definition to use
     * @return List of action steps to execute
     */
    public List<ActionStep> createPlan(String targetUrl, List<String> goals, PersonaDefinition persona) {
        if (persona == null) {
            return createPlan(targetUrl, goals, TestPersona.STANDARD);
        }

        // When no goals are provided, generate a default exploratory goal based on persona name
        List<String> effectiveGoals = (goals == null || goals.isEmpty())
                ? List.of(buildDefaultGoalForPersonaDefinition(targetUrl, persona))
                : goals;

        log.info("Creating plan for {} with {} goals using {} persona (PersonaDefinition, original goals: {})",
                targetUrl, effectiveGoals.size(), persona.name(), goals != null ? goals.size() : 0);

        String memoryContext = buildMemoryContext();
        List<ActionStep> plan = new ArrayList<>();
        plan.add(ActionStepFactory.navigate(targetUrl));

        for (String goal : effectiveGoals) {
            List<ActionStep> goalSteps = geminiClient.planGoal(goal, targetUrl, persona, memoryContext);
            plan.addAll(goalSteps);
        }

        log.info("Created plan with {} steps", plan.size());
        return plan;
    }

    /**
     * Generates steps to achieve a single goal using a PersonaDefinition.
     *
     * @param goal     The goal to achieve
     * @param snapshot Current DOM snapshot
     * @param persona  The persona definition to use
     * @return List of action steps
     */
    public List<ActionStep> planForGoal(String goal, DomSnapshot snapshot, PersonaDefinition persona) {
        if (persona == null) {
            return planForGoal(goal, snapshot, TestPersona.STANDARD);
        }
        log.debug("Planning steps for goal: {} with {} persona (PersonaDefinition)", goal, persona.name());
        String memoryContext = buildMemoryContext();
        return geminiClient.planGoalWithContext(goal, snapshot, persona, memoryContext);
    }

    /**
     * Generates repair steps using a PersonaDefinition.
     *
     * @param failedStep The step that failed
     * @param error      Error message
     * @param snapshot   Current DOM snapshot
     * @param persona    The persona definition to use
     * @return List of repair steps
     */
    public List<ActionStep> createRepairPlan(
            ActionStep failedStep,
            String error,
            DomSnapshot snapshot,
            PersonaDefinition persona
    ) {
        if (persona == null) {
            return createRepairPlan(failedStep, error, snapshot, TestPersona.STANDARD);
        }
        log.info("Creating repair plan for failed step: {} with {} persona (PersonaDefinition)", failedStep.action(), persona.name());
        String memoryContext = buildMemoryContext();
        return geminiClient.planRepair(failedStep, error, snapshot, persona, memoryContext);
    }

    /**
     * Interface for Gemini AI planning operations.
     *
     * <p>All methods accept a TestPersona whose system prompt should be prepended
     * to the AI conversation to shape the agent's behavior, plus an optional
     * memory context containing accumulated wisdom from the Global Hippocampus.
     */
    public interface GeminiPlannerClient {

        /**
         * Plans steps to achieve a goal with persona and memory context.
         *
         * @param goal          The goal to achieve
         * @param targetUrl     The target URL
         * @param persona       The test persona (use getSystemPrompt() for AI context)
         * @param memoryContext Optional accumulated wisdom from Global Hippocampus
         * @return List of action steps
         */
        List<ActionStep> planGoal(String goal, String targetUrl, TestPersona persona, String memoryContext);

        /**
         * Plans steps with DOM context, persona, and memory.
         *
         * @param goal          The goal to achieve
         * @param snapshot      Current DOM state
         * @param persona       The test persona
         * @param memoryContext Optional accumulated wisdom from Global Hippocampus
         * @return List of action steps
         */
        List<ActionStep> planGoalWithContext(String goal, DomSnapshot snapshot, TestPersona persona, String memoryContext);

        /**
         * Finds a selector for an element.
         *
         * @param elementDescription Natural language description
         * @param snapshot          Current DOM state
         * @return Optional containing the selector if found
         */
        Optional<String> findSelector(String elementDescription, DomSnapshot snapshot);

        /**
         * Plans repair steps for a failed action with persona and memory context.
         *
         * @param failedStep    The step that failed
         * @param error         Error message
         * @param snapshot      Current DOM state
         * @param persona       The test persona
         * @param memoryContext Optional accumulated wisdom from Global Hippocampus
         * @return List of repair steps
         */
        List<ActionStep> planRepair(ActionStep failedStep, String error, DomSnapshot snapshot, TestPersona persona, String memoryContext);

        // ========== PersonaDefinition overloads ==========

        /**
         * Plans steps to achieve a goal with PersonaDefinition and memory context.
         */
        default List<ActionStep> planGoal(String goal, String targetUrl, PersonaDefinition persona, String memoryContext) {
            // Default implementation delegates to TestPersona-based method using persona data
            // Implementations should override for full support
            return planGoal(goal, targetUrl, TestPersona.STANDARD, memoryContext);
        }

        /**
         * Plans steps with DOM context using PersonaDefinition.
         */
        default List<ActionStep> planGoalWithContext(String goal, DomSnapshot snapshot, PersonaDefinition persona, String memoryContext) {
            return planGoalWithContext(goal, snapshot, TestPersona.STANDARD, memoryContext);
        }

        /**
         * Plans repair steps using PersonaDefinition.
         */
        default List<ActionStep> planRepair(ActionStep failedStep, String error, DomSnapshot snapshot, PersonaDefinition persona, String memoryContext) {
            return planRepair(failedStep, error, snapshot, TestPersona.STANDARD, memoryContext);
        }
    }
}
