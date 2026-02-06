package com.ai2qa.application.ai;

import com.ai2qa.application.metrics.AiMetricsService;
import com.ai2qa.application.planner.StepPlanner;
import com.ai2qa.application.port.AiProviderInfo;
import com.ai2qa.application.port.AiResponse;
import com.ai2qa.application.port.BrowserModeProvider;
import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.security.PromptSanitizer;
import com.ai2qa.domain.context.TenantContext;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Gemini AI client for test planning operations.
 *
 * <p>Implements the GeminiPlannerClient interface using Spring AI.
 */
@Component
public class GeminiClient implements StepPlanner.GeminiPlannerClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final ChatClientPort plannerClient;
    private final ChatClientPort selectorClient;
    private final ChatClientPort repairClient;
    private final ObjectMapper objectMapper;
    private final BrowserModeProvider browserModeProvider;
    private final AiMetricsService metricsService;
    private final AiProviderInfo providerInfo;
    private final PromptSanitizer promptSanitizer;

    public GeminiClient(
            @Qualifier("plannerChatPort") ChatClientPort plannerClient,
            @Qualifier("selectorChatPort") ChatClientPort selectorClient,
            @Qualifier("repairChatPort") ChatClientPort repairClient,
            ObjectMapper objectMapper,
            BrowserModeProvider browserModeProvider,
            AiMetricsService metricsService,
            AiProviderInfo providerInfo,
            PromptSanitizer promptSanitizer
    ) {
        this.plannerClient = plannerClient;
        this.selectorClient = selectorClient;
        this.repairClient = repairClient;
        this.objectMapper = objectMapper;
        this.browserModeProvider = browserModeProvider;
        this.metricsService = metricsService;
        this.providerInfo = providerInfo;
        this.promptSanitizer = promptSanitizer;
        log.info("GeminiClient initialized with browser mode: engine={}, aria={}, promptSanitizer=enabled",
                browserModeProvider.getEngine(), browserModeProvider.isAriaEffectivelyEnabled());
    }

    @Override
    public List<ActionStep> planGoal(String goal, String targetUrl, TestPersona persona, String memoryContext) {
        log.info("Planning goal: {} for {} with {} persona (temperature: {})",
                goal, targetUrl, persona, persona.getTemperature());
        String tenantId = TenantContext.getTenantId();

        try {
            String prompt = PromptTemplates.goalPlanningPrompt(goal, targetUrl);
            String systemPrompt = buildSystemPrompt(persona.getSystemPrompt(), memoryContext);

            AiResponse response = plannerClient.callWithMetrics(systemPrompt, prompt, persona.getTemperature());

            metricsService.recordPlanGeneration(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            String rawResponse = response.content();
            log.info("AI response received ({} chars) for goal: '{}'. Preview: {}",
                    rawResponse != null ? rawResponse.length() : 0, goal,
                    truncate(rawResponse, 500));

            List<ActionStep> steps = parseStepsResponse(rawResponse);

            // IMPORTANT: If parsing returns empty, use fallback plan
            // This handles cases where AI returns empty array, wrong format, or invalid JSON
            if (steps.isEmpty()) {
                log.error("AI PLAN GENERATION FAILED for goal: '{}'\n" +
                        "  Raw response (first 500 chars): {}\n" +
                        "  Provider: {}, Model: {}\n" +
                        "  This indicates the AI is not returning valid JSON array format.",
                        goal, truncate(rawResponse, 500), providerInfo.provider(), providerInfo.model());
                return createFallbackPlanForGoal(goal, targetUrl);
            }

            log.info("Successfully generated {} steps for goal: '{}'", steps.size(), goal);
            return steps;

        } catch (Exception e) {
            log.error("Failed to plan goal: {} - {}", goal, e.getMessage(), e);
            return createFallbackPlanForGoal(goal, targetUrl);
        }
    }

    /**
     * Creates a fallback plan when AI fails to generate steps for a goal.
     *
     * <p>IMPORTANT: Fallback plan should only use ACHIEVABLE actions that don't
     * depend on finding specific elements (which would fail). Uses:
     * - wait (always succeeds)
     * - screenshot (always succeeds)
     * - navigate (if goal mentions URLs)
     *
     * <p>Does NOT use click/type with vague targets as these will fail element finding.
     */
    private List<ActionStep> createFallbackPlanForGoal(String goal, String targetUrl) {
        log.warn("AI failed to generate plan - using fallback for goal: '{}'", goal);

        List<ActionStep> fallbackSteps = new ArrayList<>();

        // Wait for page to stabilize (always succeeds)
        fallbackSteps.add(ActionStepFactory.waitFor("page to stabilize", 3000));

        // Take screenshot to document initial state (always succeeds)
        fallbackSteps.add(ActionStepFactory.screenshot("Page state - AI could not plan: " + truncate(goal, 50)));

        // Add more wait time for dynamic content
        fallbackSteps.add(ActionStepFactory.waitFor("dynamic content to load", 2000));

        // Final screenshot (always succeeds)
        fallbackSteps.add(ActionStepFactory.screenshot("Final state - manual review needed for: " + truncate(goal, 50)));

        // Log clearly that this is a degraded experience
        log.warn("Fallback plan created with {} basic steps. Goal '{}' requires manual review.",
                fallbackSteps.size(), truncate(goal, 100));

        return fallbackSteps;
    }

    @Override
    public List<ActionStep> planGoalWithContext(String goal, DomSnapshot snapshot, TestPersona persona, String memoryContext) {
        log.info("Planning goal with context: {} using {} persona (temperature: {})",
                goal, persona, persona.getTemperature());
        String tenantId = TenantContext.getTenantId();

        try {
            // SECURITY: Sanitize DOM content to prevent indirect prompt injection
            String sanitizedContent = promptSanitizer.sanitizeAndLabel(
                    truncate(snapshot.content(), 3000),
                    "DOM_SNAPSHOT"
            );

            String prompt = String.format("""
                Create steps to achieve this goal: %s

                Current page state:
                URL: %s
                Title: %s

                %s

                Provide steps as JSON array.
                """,
                    goal,
                    snapshot.url(),
                    snapshot.title(),
                    sanitizedContent
            );

            String systemPrompt = buildSystemPrompt(persona.getSystemPrompt(), memoryContext);
            AiResponse response = plannerClient.callWithMetrics(systemPrompt, prompt, persona.getTemperature());

            metricsService.recordPlanGeneration(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            return parseStepsResponse(response.content());

        } catch (Exception e) {
            log.error("Failed to plan goal with context: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<String> findSelector(String elementDescription, DomSnapshot snapshot) {
        boolean useAriaMode = browserModeProvider.isAriaEffectivelyEnabled();
        log.debug("Finding {} for: {}", useAriaMode ? "ref" : "selector", elementDescription);
        String tenantId = TenantContext.getTenantId();

        try {
            // SECURITY: Sanitize DOM content to prevent indirect prompt injection
            String sanitizedContent = promptSanitizer.sanitizeText(snapshot.content());

            String prompt = PromptTemplates.selectorFinderPrompt(
                    elementDescription,
                    sanitizedContent,
                    useAriaMode
            );

            AiResponse response = selectorClient.callWithMetrics(prompt);

            // Clean up response - extract actual selector from potential reasoning text
            String selectorOrRef = extractSelectorFromResponse(response.content(), useAriaMode);

            boolean found = !"NOT_FOUND".equalsIgnoreCase(selectorOrRef) && !selectorOrRef.isBlank();

            if (found) {
                metricsService.recordElementFind(
                        tenantId, null, providerInfo.provider(), response.modelName(),
                        response.inputTokens(), response.outputTokens(),
                        (int) response.latencyMs(), false);
                log.debug("Found {}: {} for: {}", useAriaMode ? "ref" : "selector", selectorOrRef, elementDescription);
                return Optional.of(selectorOrRef);
            } else {
                metricsService.recordElementFindFailure(
                        tenantId, null, providerInfo.provider(), response.modelName(),
                        response.inputTokens(), response.outputTokens(),
                        (int) response.latencyMs(), false, "NOT_FOUND");
                log.debug("Element not found for: {}", elementDescription);
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("Failed to find element: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<ActionStep> planRepair(ActionStep failedStep, String error, DomSnapshot snapshot, TestPersona persona, String memoryContext) {
        log.info("Planning repair for failed step: {} with {} persona (temperature: {})",
                failedStep.action(), persona, persona.getTemperature());
        String tenantId = TenantContext.getTenantId();

        try {
            // SECURITY: Sanitize DOM content to prevent indirect prompt injection
            String sanitizedContent = promptSanitizer.sanitizeText(snapshot.content());

            String prompt = PromptTemplates.repairPlanningPrompt(
                    failedStep.action(),
                    failedStep.target(),
                    error,
                    sanitizedContent
            );

            String systemPrompt = buildSystemPrompt(persona.getSystemPrompt(), memoryContext);
            AiResponse response = repairClient.callWithMetrics(systemPrompt, prompt, persona.getTemperature());

            metricsService.recordRepairPlan(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            return parseStepsResponse(response.content());

        } catch (Exception e) {
            log.error("Failed to plan repair: {}", e.getMessage());
            // Return simple retry as fallback
            return List.of(
                    ActionStepFactory.waitFor("recovery", 2000),
                    ActionStepFactory.withSelector(failedStep, null)
            );
        }
    }

    // ========== PersonaDefinition overloads ==========

    @Override
    public List<ActionStep> planGoal(String goal, String targetUrl, PersonaDefinition persona, String memoryContext) {
        log.info("Planning goal: {} for {} with {} persona (PersonaDefinition, temperature: {})",
                goal, targetUrl, persona.name(), persona.temperature());
        String tenantId = TenantContext.getTenantId();

        try {
            String prompt = PromptTemplates.goalPlanningPrompt(goal, targetUrl);
            String systemPrompt = buildSystemPrompt(persona.systemPrompt(), memoryContext);

            AiResponse response = plannerClient.callWithMetrics(systemPrompt, prompt, persona.temperature());

            metricsService.recordPlanGeneration(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            String rawResponse = response.content();
            List<ActionStep> steps = parseStepsResponse(rawResponse);

            if (steps.isEmpty()) {
                log.error("AI PLAN GENERATION FAILED (PersonaDefinition) for goal: '{}'", goal);
                return createFallbackPlanForGoal(goal, targetUrl);
            }

            log.info("Successfully generated {} steps for goal: '{}' (PersonaDefinition)", steps.size(), goal);
            return steps;

        } catch (Exception e) {
            log.error("Failed to plan goal (PersonaDefinition): {} - {}", goal, e.getMessage(), e);
            return createFallbackPlanForGoal(goal, targetUrl);
        }
    }

    @Override
    public List<ActionStep> planGoalWithContext(String goal, DomSnapshot snapshot, PersonaDefinition persona, String memoryContext) {
        log.info("Planning goal with context: {} using {} persona (PersonaDefinition, temperature: {})",
                goal, persona.name(), persona.temperature());
        String tenantId = TenantContext.getTenantId();

        try {
            String sanitizedContent = promptSanitizer.sanitizeAndLabel(
                    truncate(snapshot.content(), 3000), "DOM_SNAPSHOT");

            String prompt = String.format("""
                Create steps to achieve this goal: %s

                Current page state:
                URL: %s
                Title: %s

                %s

                Provide steps as JSON array.
                """,
                    goal, snapshot.url(), snapshot.title(), sanitizedContent);

            String systemPrompt = buildSystemPrompt(persona.systemPrompt(), memoryContext);
            AiResponse response = plannerClient.callWithMetrics(systemPrompt, prompt, persona.temperature());

            metricsService.recordPlanGeneration(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            return parseStepsResponse(response.content());

        } catch (Exception e) {
            log.error("Failed to plan goal with context (PersonaDefinition): {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ActionStep> planRepair(ActionStep failedStep, String error, DomSnapshot snapshot, PersonaDefinition persona, String memoryContext) {
        log.info("Planning repair for failed step: {} with {} persona (PersonaDefinition, temperature: {})",
                failedStep.action(), persona.name(), persona.temperature());
        String tenantId = TenantContext.getTenantId();

        try {
            String sanitizedContent = promptSanitizer.sanitizeText(snapshot.content());

            String prompt = PromptTemplates.repairPlanningPrompt(
                    failedStep.action(), failedStep.target(), error, sanitizedContent);

            String systemPrompt = buildSystemPrompt(persona.systemPrompt(), memoryContext);
            AiResponse response = repairClient.callWithMetrics(systemPrompt, prompt, persona.temperature());

            metricsService.recordRepairPlan(
                    tenantId, null, providerInfo.provider(), response.modelName(),
                    response.inputTokens(), response.outputTokens(),
                    (int) response.latencyMs(), true, null);

            return parseStepsResponse(response.content());

        } catch (Exception e) {
            log.error("Failed to plan repair (PersonaDefinition): {}", e.getMessage());
            return List.of(
                    ActionStepFactory.waitFor("recovery", 2000),
                    ActionStepFactory.withSelector(failedStep, null)
            );
        }
    }

    /**
     * Parses JSON response into ActionStep list.
     *
     * <p>Handles truncated JSON responses by attempting to recover valid steps
     * from partially complete JSON arrays. This is important for long responses
     * that may be cut off by token limits.
     */
    private List<ActionStep> parseStepsResponse(String response) {
        try {
            // Extract JSON from response (may be wrapped in markdown code blocks)
            String json = extractJson(response);

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }

            return StreamSupport.stream(root.spliterator(), false)
                    .map(this::mapToActionStep)
                    .filter(step -> !isRedundantNavigateStep(step))
                    .toList();

        } catch (JsonProcessingException e) {
            log.warn("Initial JSON parse failed: {} - attempting truncation recovery", e.getMessage());
            return attemptTruncatedJsonRecovery(response);
        }
    }

    /**
     * Attempts to recover valid steps from truncated JSON.
     *
     * <p>When AI generates very long responses, they may be truncated mid-JSON.
     * This method tries to find the last complete JSON object in the array
     * and parse what we have.
     */
    private List<ActionStep> attemptTruncatedJsonRecovery(String response) {
        try {
            String json = extractJson(response);

            // Find the last complete object by looking for "},\n" or "}\n]" patterns
            int lastCompleteObject = findLastCompleteObject(json);
            if (lastCompleteObject <= 0) {
                log.error("Could not recover any complete steps from truncated JSON");
                return List.of();
            }

            // Build a valid JSON array from the complete portion
            String recoveredJson = json.substring(0, lastCompleteObject + 1) + "]";
            log.info("Attempting to parse recovered JSON ({} chars from original {} chars)",
                    recoveredJson.length(), json.length());

            JsonNode root = objectMapper.readTree(recoveredJson);
            if (!root.isArray()) {
                return List.of();
            }

            List<ActionStep> steps = StreamSupport.stream(root.spliterator(), false)
                    .map(this::mapToActionStep)
                    .filter(step -> !isRedundantNavigateStep(step))
                    .toList();

            if (!steps.isEmpty()) {
                log.info("Successfully recovered {} steps from truncated JSON response", steps.size());
            }
            return steps;

        } catch (JsonProcessingException e) {
            log.error("Failed to recover from truncated JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Finds the index of the last complete JSON object in a truncated array.
     * Looks for patterns like "}," or "}" followed by whitespace.
     */
    private int findLastCompleteObject(String json) {
        // Look for the last occurrence of "}," which indicates a complete object
        int lastComma = json.lastIndexOf("},");
        if (lastComma > 0) {
            return lastComma; // Return position of the closing brace
        }

        // If no comma, look for a standalone "}" that might be the last complete object
        int lastBrace = json.lastIndexOf("}");
        if (lastBrace > 0 && lastBrace < json.length() - 1) {
            // Check if there's more content after this brace (indicating truncation)
            String afterBrace = json.substring(lastBrace + 1).trim();
            if (!afterBrace.isEmpty() && !afterBrace.equals("]")) {
                return lastBrace;
            }
        }

        return -1;
    }

    /**
     * Extracts JSON from potentially markdown-wrapped response.
     *
     * <p>Gemini may include explanatory text before/after the JSON array
     * (unlike Claude which follows format instructions more strictly).
     * This method strips markdown fences and locates the JSON array boundaries.
     */
    private String extractJson(String response) {
        // Remove markdown code block wrappers if present
        String json = response.trim();

        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }

        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }

        json = json.trim();

        // Find actual JSON array boundaries if text surrounds it
        // e.g. "Here are the steps:\n[{...}]\nLet me know if..."
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart && arrayStart > 0) {
            log.debug("Extracting JSON array from position {} to {} (text before: {} chars)",
                    arrayStart, arrayEnd, arrayStart);
            json = json.substring(arrayStart, arrayEnd + 1);
        }

        return json.trim();
    }

    /**
     * Maps a raw map to ActionStep.
     *
     * <p>Handles special cases:
     * <ul>
     *   <li>Navigate steps: Uses value as target if target is empty (AI may put URL in value)</li>
     *   <li>Empty targets: Filters out steps with empty action or target</li>
     * </ul>
     */
    private ActionStep mapToActionStep(JsonNode node) {
        String action = node.path("action").asText("");
        String target = node.path("target").asText("");
        String selector = node.path("selector").asText(null);
        String value = node.path("value").asText(null);
        Map<String, String> params = extractParams(node.path("params"));

        // For navigate actions, use value as target if target is empty
        // AI may put URL in value field per ARCHITECT_JSON_SCHEMA spec
        if ("navigate".equalsIgnoreCase(action) && target.isBlank() && value != null && !value.isBlank()) {
            target = value;
            log.debug("Navigate step: using value '{}' as target (target was empty)", value);
        }

        return ActionStepFactory.reconstitute(
                "step-" + System.nanoTime(),
                action,
                target,
                selector,
                value,
                params
        );
    }

    /**
     * Checks if a step is a redundant navigate step that should be filtered out.
     *
     * <p>StepPlanner already adds the initial navigate step to the target URL.
     * Any navigate steps from AI response are redundant and should be filtered.
     */
    private boolean isRedundantNavigateStep(ActionStep step) {
        if ("navigate".equalsIgnoreCase(step.action())) {
            log.debug("Filtering out redundant navigate step (target: '{}') - StepPlanner handles navigation",
                    step.target());
            return true;
        }
        return false;
    }

    private Map<String, String> extractParams(JsonNode paramsNode) {
        if (paramsNode == null || paramsNode.isMissingNode() || !paramsNode.isObject()) {
            return Map.of();
        }
        Map<String, String> params = new HashMap<>();
        paramsNode.fields().forEachRemaining(entry -> params.put(entry.getKey(), entry.getValue().asText("")));
        return Map.copyOf(params);
    }

    /**
     * Truncates text to max length.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Combines persona system prompt with optional memory context.
     *
     * @param personaPrompt The persona's base system prompt
     * @param memoryContext Optional accumulated wisdom from Global Hippocampus
     * @return Combined system prompt
     */
    private String buildSystemPrompt(String personaPrompt, String memoryContext) {
        if (memoryContext == null || memoryContext.isBlank()) {
            return personaPrompt;
        }
        return personaPrompt + memoryContext;
    }

    /**
     * Extracts the actual selector or ref from an AI response.
     *
     * <p>Some AI models (especially Claude) may output their reasoning/thinking
     * along with the selector. This method extracts just the selector portion.
     *
     * <p>For aria mode, looks for patterns like: @e1, @ref1, ref_123
     * <p>For CSS mode, looks for patterns like: #id, .class, [attr], button, a
     *
     * @param rawResponse The raw AI response which may contain reasoning
     * @param useAriaMode Whether to look for aria refs or CSS selectors
     * @return The extracted selector/ref, or NOT_FOUND if extraction fails
     */
    private String extractSelectorFromResponse(String rawResponse, boolean useAriaMode) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "NOT_FOUND";
        }

        String trimmed = rawResponse.trim();

        // Quick check: if response is short and clean, use it directly
        if (trimmed.length() < 100 && !trimmed.contains("\n") && !trimmed.toLowerCase().startsWith("looking")) {
            return trimmed.replaceAll("[\"']", "");
        }

        // Response contains reasoning - need to extract the selector
        log.warn("AI response contains reasoning text ({}+ chars), extracting selector...",
                trimmed.length());

        if (useAriaMode) {
            // Look for aria ref patterns: @e1, @ref1, ref_123, e123
            java.util.regex.Pattern refPattern = java.util.regex.Pattern.compile(
                    "@?(?:e|ref_?|E)\\d+",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher matcher = refPattern.matcher(trimmed);
            if (matcher.find()) {
                String ref = matcher.group();
                // Ensure it starts with @
                if (!ref.startsWith("@")) {
                    ref = "@" + ref;
                }
                log.info("Extracted ref from reasoning: {}", ref);
                return ref;
            }
        } else {
            // Look for CSS selector patterns
            // Priority: data-testid, id selectors, class selectors, attribute selectors, tag selectors
            java.util.regex.Pattern[] patterns = {
                    // data-testid
                    java.util.regex.Pattern.compile("\\[data-testid=[\"']?([^\"'\\]]+)[\"']?\\]"),
                    // ID selector
                    java.util.regex.Pattern.compile("#[a-zA-Z][a-zA-Z0-9_-]*"),
                    // Attribute selector
                    java.util.regex.Pattern.compile("\\[[a-zA-Z-]+=[\"']?[^\"'\\]]+[\"']?\\]"),
                    // Class selector
                    java.util.regex.Pattern.compile("\\.[a-zA-Z][a-zA-Z0-9_-]*"),
                    // Tag with selector
                    java.util.regex.Pattern.compile("(?:button|a|input|div|span)(?:#|\\.|\\[)[a-zA-Z][^\\s,]*")
            };

            for (java.util.regex.Pattern pattern : patterns) {
                java.util.regex.Matcher matcher = pattern.matcher(trimmed);
                if (matcher.find()) {
                    String selector = matcher.group();
                    log.info("Extracted CSS selector from reasoning: {}", selector);
                    return selector;
                }
            }
        }

        // Check for NOT_FOUND anywhere in response
        if (trimmed.toUpperCase().contains("NOT_FOUND") ||
                trimmed.toLowerCase().contains("element not found") ||
                trimmed.toLowerCase().contains("cannot find") ||
                trimmed.toLowerCase().contains("no matching")) {
            log.debug("AI indicated element not found");
            return "NOT_FOUND";
        }

        // Failed to extract - log the problematic response and return NOT_FOUND
        log.error("Failed to extract selector from AI reasoning. Response preview: {}",
                truncate(trimmed, 200));
        return "NOT_FOUND";
    }
}
