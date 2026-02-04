package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.security.PromptSanitizer;
import com.ai2qa.domain.model.DomSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Generates AI-powered optimization suggestions for test steps.
 *
 * <p>Analyzes console errors, network errors, and selector issues to provide
 * actionable recommendations for developers. This is a key feature for helping
 * customers understand and fix issues in their applications.
 *
 * <p>Uses "The Healer" persona to analyze execution data and suggest fixes for:
 * <ul>
 *   <li>JavaScript errors (null pointer exceptions, hydration failures)</li>
 *   <li>Network errors (API failures, timeouts)</li>
 *   <li>Selector issues (brittle selectors, missing data-testid)</li>
 *   <li>Accessibility problems (missing ARIA labels)</li>
 * </ul>
 */
@Service
public class OptimizationSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(OptimizationSuggestionService.class);
    private static final int MAX_ERRORS_TO_ANALYZE = 5;
    private static final int MAX_DOM_LENGTH = 3000;

    private final ChatClientPort chatClient;
    private final ObjectMapper objectMapper;
    private final PromptSanitizer promptSanitizer;
    private final String systemPrompt;

    public OptimizationSuggestionService(
            @Qualifier("plannerChatPort") ChatClientPort chatClient,
            ObjectMapper objectMapper,
            PromptSanitizer promptSanitizer,
            @Value("classpath:prompts/healer/system-legacy.md") Resource systemPromptResource) throws IOException {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.promptSanitizer = promptSanitizer;
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Generates an optimization suggestion based on step execution data.
     *
     * @param action           The action that was executed (click, type, etc.)
     * @param target           The target element description
     * @param selectorUsed     The selector that was used (may be null)
     * @param consoleErrors    JavaScript console errors captured during execution
     * @param networkErrors    HTTP errors captured during execution
     * @param snapshot         Current DOM snapshot
     * @param wasSuccessful    Whether the step succeeded
     * @return Optional containing the AI-generated suggestion, or empty if no suggestion needed
     */
    public Optional<String> generateSuggestion(
            String action,
            String target,
            String selectorUsed,
            List<String> consoleErrors,
            List<String> networkErrors,
            DomSnapshot snapshot,
            boolean wasSuccessful) {

        // Skip if no issues to analyze
        if (consoleErrors.isEmpty() && networkErrors.isEmpty() && wasSuccessful) {
            return Optional.empty();
        }

        try {
            String userPrompt = buildUserPrompt(
                    action, target, selectorUsed,
                    consoleErrors, networkErrors, snapshot, wasSuccessful);

            log.debug("[HEALER] Generating optimization suggestion for {} on {}", action, target);

            String response = chatClient.call(systemPrompt, userPrompt, 0.3);

            return parseResponse(response);

        } catch (Exception e) {
            log.warn("[HEALER] Failed to generate suggestion: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUserPrompt(
            String action,
            String target,
            String selectorUsed,
            List<String> consoleErrors,
            List<String> networkErrors,
            DomSnapshot snapshot,
            boolean wasSuccessful) {

        StringBuilder sb = new StringBuilder();

        sb.append("## Step Execution Analysis\n\n");
        sb.append("**Action:** ").append(action).append("\n");
        sb.append("**Target:** ").append(target).append("\n");
        sb.append("**Selector Used:** ").append(selectorUsed != null ? selectorUsed : "N/A").append("\n");
        sb.append("**Result:** ").append(wasSuccessful ? "SUCCESS" : "FAILED").append("\n\n");

        // Console errors section
        if (!consoleErrors.isEmpty()) {
            sb.append("## JavaScript Console Errors\n\n");
            sb.append("The following JavaScript errors were captured during this step:\n\n");
            consoleErrors.stream()
                    .limit(MAX_ERRORS_TO_ANALYZE)
                    .forEach(err -> sb.append("- ").append(promptSanitizer.sanitizeText(err)).append("\n"));
            if (consoleErrors.size() > MAX_ERRORS_TO_ANALYZE) {
                sb.append("- ... and ").append(consoleErrors.size() - MAX_ERRORS_TO_ANALYZE).append(" more errors\n");
            }
            sb.append("\n");
        }

        // Network errors section
        if (!networkErrors.isEmpty()) {
            sb.append("## Network Errors\n\n");
            sb.append("The following HTTP errors occurred during this step:\n\n");
            networkErrors.stream()
                    .limit(MAX_ERRORS_TO_ANALYZE)
                    .forEach(err -> sb.append("- ").append(promptSanitizer.sanitizeText(err)).append("\n"));
            if (networkErrors.size() > MAX_ERRORS_TO_ANALYZE) {
                sb.append("- ... and ").append(networkErrors.size() - MAX_ERRORS_TO_ANALYZE).append(" more errors\n");
            }
            sb.append("\n");
        }

        // DOM snapshot (truncated)
        if (snapshot != null && snapshot.content() != null && !snapshot.content().isBlank()) {
            sb.append("## DOM Context\n\n");
            sb.append("```html\n");
            String sanitizedDom = promptSanitizer.sanitizeText(snapshot.content());
            if (sanitizedDom.length() > MAX_DOM_LENGTH) {
                sanitizedDom = sanitizedDom.substring(0, MAX_DOM_LENGTH) + "\n... (truncated)";
            }
            sb.append(sanitizedDom);
            sb.append("\n```\n\n");
        }

        sb.append("## Instructions\n\n");
        sb.append("Analyze the execution data and provide an optimization suggestion.\n");
        sb.append("Focus on:\n");
        sb.append("1. If there are JS errors: Explain the likely cause and suggest a fix\n");
        sb.append("2. If there are network errors: Identify if it's a backend issue\n");
        sb.append("3. If the selector seems brittle: Suggest a better selector (data-testid, aria-label)\n");
        sb.append("4. If accessibility issues are apparent: Suggest ARIA improvements\n\n");
        sb.append("Respond with JSON:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"hasSuggestion\": true,\n");
        sb.append("  \"suggestion\": \"Your concise, actionable suggestion here\",\n");
        sb.append("  \"rootCause\": \"FRONTEND|BACKEND|NETWORK|SELECTOR|ACCESSIBILITY\",\n");
        sb.append("  \"severity\": \"LOW|MEDIUM|HIGH\"\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    private Optional<String> parseResponse(String response) {
        try {
            String json = extractJson(response);
            SuggestionResponse parsed = objectMapper.readValue(json, SuggestionResponse.class);

            if (!parsed.hasSuggestion || parsed.suggestion == null || parsed.suggestion.isBlank()) {
                log.debug("[HEALER] No suggestion needed");
                return Optional.empty();
            }

            log.info("[HEALER] Generated suggestion ({}): {}", parsed.rootCause, truncate(parsed.suggestion, 100));
            return Optional.of(parsed.suggestion);

        } catch (JsonProcessingException e) {
            log.warn("[HEALER] Failed to parse response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";

        String trimmed = response.trim();

        // Remove markdown code block if present
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        return trimmed.trim();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SuggestionResponse {
        public boolean hasSuggestion;
        public String suggestion;
        public String rootCause;
        public String severity;
    }
}
