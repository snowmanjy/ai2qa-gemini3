package com.ai2qa.application.memory;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorType;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Librarian - extracts reusable insights from completed test runs.
 *
 * <p>Analyzes test execution results and generates insights that can help
 * future test runs. These insights are stored in the Global Hippocampus
 * (agent_memory table) for cross-run learning.
 */
@Service
public class LibrarianService {

    private static final Logger log = LoggerFactory.getLogger(LibrarianService.class);
    private static final int MAX_CONTEXT_LENGTH = 8000;

    private final ChatClientPort chatClient;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final String jsonSchema;

    public LibrarianService(
            @Qualifier("plannerChatPort") ChatClientPort chatClient,
            ObjectMapper objectMapper,
            @Value("classpath:prompts/librarian/system.md") Resource systemPromptResource,
            @Value("classpath:prompts/librarian/json-schema.md") Resource jsonSchemaResource) throws IOException {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
        this.jsonSchema = jsonSchemaResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Extracts insights from a completed test run.
     *
     * @param testRun The completed test run to analyze
     * @return List of extracted insights, or empty list if none found
     */
    public List<ExtractedInsight> extractInsights(TestRun testRun) {
        return extractLearnings(testRun).insights();
    }

    /**
     * Extracts both text insights and structured patterns from a completed test run.
     *
     * @param testRun The completed test run to analyze
     * @return Combined extraction result with insights and patterns
     */
    public ExtractionResult extractLearnings(TestRun testRun) {
        if (!isLearnable(testRun)) {
            log.debug("Test run {} is not learnable, skipping insight extraction", testRun.getId());
            return ExtractionResult.empty();
        }

        try {
            String userPrompt = buildUserPrompt(testRun);
            String fullSystemPrompt = systemPrompt + "\n\n" + jsonSchema;

            log.info("[LIBRARIAN] Analyzing test run {} for insights...", testRun.getId());
            String response = chatClient.call(fullSystemPrompt, userPrompt, 0.3); // Low temperature for consistency

            return parseFullResponse(response, extractDomain(testRun.getTargetUrl()));

        } catch (Exception e) {
            log.warn("[LIBRARIAN] Failed to extract insights from run {}: {}",
                    testRun.getId(), e.getMessage());
            return ExtractionResult.empty();
        }
    }

    /**
     * Extracts the domain from a URL.
     */
    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Determines if a test run can provide valuable learning.
     *
     * <p>Learning priority:
     * <ol>
     *   <li>Highest: Runs with self-healed steps (retryCount > 0)</li>
     *   <li>Medium: Successful runs (COMPLETED status)</li>
     *   <li>Skip: Pure failures with no self-healing (no actionable patterns)</li>
     * </ol>
     */
    private boolean isLearnable(TestRun testRun) {
        // Only consider completed or failed runs (not cancelled/pending)
        TestRunStatus status = testRun.getStatus();
        if (status != TestRunStatus.COMPLETED && status != TestRunStatus.FAILED) {
            return false;
        }

        // Need at least some executed steps to learn from
        if (testRun.getExecutedSteps().isEmpty()) {
            return false;
        }

        // Check if run has self-healed steps (highest value learning)
        boolean hasSelfHealedSteps = testRun.getExecutedSteps().stream()
                .anyMatch(step -> step.retryCount() > 0);

        // For FAILED runs: only learn if there were self-healed steps
        // Pure failures without healing provide low-value/wrong lessons
        if (status == TestRunStatus.FAILED) {
            if (!hasSelfHealedSteps) {
                log.debug("Skipping learning from pure failure (no self-healed steps): {}", testRun.getId());
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if the run contains self-healed steps (high-value learning).
     */
    private boolean hasSelfHealedSteps(TestRun testRun) {
        return testRun.getExecutedSteps().stream()
                .anyMatch(step -> step.retryCount() > 0);
    }

    /**
     * Builds the user prompt with test run details.
     */
    private String buildUserPrompt(TestRun testRun) {
        StringBuilder sb = new StringBuilder();

        // Count self-healed steps for emphasis
        long selfHealedCount = testRun.getExecutedSteps().stream()
                .filter(step -> step.retryCount() > 0)
                .count();

        sb.append("## Test Run Summary\n\n");
        sb.append("**Target URL:** ").append(sanitizeUrl(testRun.getTargetUrl())).append("\n");
        sb.append("**Status:** ").append(testRun.getStatus()).append("\n");
        sb.append("**Persona:** ").append(testRun.getPersona()).append("\n");

        if (selfHealedCount > 0) {
            sb.append("**Self-Healed Steps:** ").append(selfHealedCount)
              .append(" (HIGH VALUE - focus learning on these!)\n");
        }

        testRun.getFailureReason().ifPresent(reason ->
            sb.append("**Failure Reason:** ").append(sanitizeText(reason)).append("\n"));

        sb.append("\n## Goals\n");
        for (String goal : testRun.getGoals()) {
            sb.append("- ").append(sanitizeText(goal)).append("\n");
        }

        sb.append("\n## Executed Steps\n\n");
        List<ExecutedStep> steps = testRun.getExecutedSteps();

        // Limit steps to avoid exceeding context
        int maxSteps = Math.min(steps.size(), 20);
        for (int i = 0; i < maxSteps; i++) {
            ExecutedStep step = steps.get(i);
            sb.append(formatStep(i + 1, step));
        }

        if (steps.size() > maxSteps) {
            sb.append("\n... (").append(steps.size() - maxSteps).append(" more steps truncated)\n");
        }

        // Truncate if too long
        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_LENGTH) {
            result = result.substring(0, MAX_CONTEXT_LENGTH) + "\n... (truncated)";
        }

        return result;
    }

    /**
     * Formats a single executed step for the prompt.
     */
    private String formatStep(int index, ExecutedStep step) {
        StringBuilder sb = new StringBuilder();

        // Highlight self-healed steps prominently
        boolean isSelfHealed = step.retryCount() > 0;
        if (isSelfHealed) {
            sb.append("### Step ").append(index).append(": ").append(step.action())
              .append(" ⚡ SELF-HEALED (HIGH VALUE)\n");
        } else {
            sb.append("### Step ").append(index).append(": ").append(step.action()).append("\n");
        }

        sb.append("- **Status:** ").append(step.status()).append("\n");
        sb.append("- **Target:** ").append(sanitizeText(step.target())).append("\n");

        step.selectorUsedOpt().ifPresent(selector ->
            sb.append("- **Selector Used:** `").append(sanitizeText(selector)).append("`\n"));

        step.errorMessageOpt().ifPresent(error ->
            sb.append("- **Error:** ").append(sanitizeText(error)).append("\n"));

        if (isSelfHealed) {
            sb.append("- **Retries:** ").append(step.retryCount())
              .append(" (Learn: what selector/approach finally worked?)\n");
        }

        if (step.hasConsoleErrors()) {
            sb.append("- **Console Errors:** ")
              .append(step.consoleErrors().stream()
                  .limit(3)
                  .map(this::sanitizeText)
                  .collect(Collectors.joining("; ")))
              .append("\n");
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * Parses the AI response into insights.
     */
    private List<ExtractedInsight> parseResponse(String response) {
        return parseFullResponse(response, "").insights();
    }

    /**
     * Parses the AI response into insights and structured patterns.
     */
    private ExtractionResult parseFullResponse(String response, String domain) {
        try {
            // Extract JSON from response (may have markdown)
            String json = extractJson(response);
            LibrarianResponse parsed = objectMapper.readValue(json, LibrarianResponse.class);

            // Parse text insights
            List<ExtractedInsight> insights = List.of();
            if (parsed.insights != null) {
                insights = parsed.insights.stream()
                        .filter(this::isValidInsight)
                        .map(i -> new ExtractedInsight(
                                sanitizeTag(i.contextTag),
                                sanitizeText(i.insightText)))
                        .limit(5) // Max 5 insights per run
                        .toList();
            }

            // Parse structured patterns
            List<ExtractedPattern> patterns = List.of();
            if (parsed.sitePatterns != null) {
                patterns = parsed.sitePatterns.stream()
                        .filter(this::isValidPattern)
                        .map(p -> toExtractedPattern(p, domain))
                        .limit(10) // Max 10 patterns per run
                        .toList();
            }

            log.info("[LIBRARIAN] Extracted {} insights and {} patterns (confidence: {})",
                    insights.size(), patterns.size(), parsed.learningConfidence);

            return new ExtractionResult(
                    insights,
                    patterns,
                    Optional.ofNullable(parsed.frameworkDetected),
                    Optional.ofNullable(parsed.learningConfidence).orElse("low")
            );

        } catch (JsonProcessingException e) {
            log.warn("[LIBRARIAN] Failed to parse response: {}", e.getMessage());
            return ExtractionResult.empty();
        }
    }

    /**
     * Validates a pattern before storing.
     */
    private boolean isValidPattern(SitePatternDto pattern) {
        if (pattern.patternType == null || pattern.key == null || pattern.key.isBlank()) {
            return false;
        }
        // SELECTOR patterns must have a selector value
        if ("SELECTOR".equals(pattern.patternType)) {
            return pattern.selector != null && !pattern.selector.isBlank();
        }
        // Other patterns must have a value
        return pattern.value != null && !pattern.value.isBlank();
    }

    /**
     * Converts a DTO to an ExtractedPattern.
     */
    private ExtractedPattern toExtractedPattern(SitePatternDto dto, String domain) {
        PatternType type = parsePatternType(dto.patternType);
        String value = type == PatternType.SELECTOR ? dto.selector : dto.value;
        SelectorType selectorType = parseSelectorType(dto.selectorType);

        List<AlternativeSelector> alternatives = List.of();
        if (dto.alternatives != null) {
            alternatives = dto.alternatives.stream()
                    .filter(alt -> alt.selector != null && !alt.selector.isBlank())
                    .map(alt -> new AlternativeSelector(
                            alt.selector,
                            parseSelectorType(alt.selectorType)))
                    .toList();
        }

        return new ExtractedPattern(
                domain,
                type,
                sanitizeKey(dto.key),
                sanitizeText(value),
                selectorType,
                Optional.ofNullable(dto.description).map(this::sanitizeText),
                alternatives
        );
    }

    private PatternType parsePatternType(String type) {
        if (type == null) return PatternType.QUIRK;
        return switch (type.toUpperCase()) {
            case "SELECTOR" -> PatternType.SELECTOR;
            case "TIMING" -> PatternType.TIMING;
            case "AUTH" -> PatternType.AUTH;
            default -> PatternType.QUIRK;
        };
    }

    private SelectorType parseSelectorType(String type) {
        if (type == null) return SelectorType.CSS;
        return switch (type.toUpperCase()) {
            case "XPATH" -> SelectorType.XPATH;
            case "TEXT" -> SelectorType.TEXT;
            case "ARIA" -> SelectorType.ARIA;
            case "DATA_TESTID" -> SelectorType.DATA_TESTID;
            default -> SelectorType.CSS;
        };
    }

    private String sanitizeKey(String key) {
        if (key == null) return "unknown";
        return key.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .substring(0, Math.min(key.length(), 100));
    }

    /**
     * Extracts JSON from a response that may contain markdown.
     */
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

    // Minimum insight length to filter out shallow/useless insights
    private static final int MIN_INSIGHT_LENGTH = 100;

    // Generic tags that provide no actionable value (exact matches after normalization)
    private static final java.util.Set<String> BLOCKED_TAGS = java.util.Set.of(
            // Wait patterns - generic advice everyone knows
            "wait:page_load", "wait:page-load", "wait:navigation", "wait:ready",
            "wait:dynamic", "wait:load", "wait:content", "wait:element",
            // Error patterns - too generic
            "error:element_not_found", "error:element-not-found", "error:elementnotfound",
            "error:timeout", "error:navigation", "error:click", "error:generic",
            // Healed patterns - too vague
            "healed:navigation", "healed:element", "healed:selector", "healed:click",
            // Selector patterns - basic knowledge, not insights
            "selector:css", "selector:xpath", "selector:aria_label", "selector:aria-label",
            "selector:id", "selector:class", "selector:data_testid", "selector:data-testid",
            "selector:text", "selector:role", "selector:name", "selector:button",
            // Site patterns - useless categorizations
            "site:media", "site:news", "site:ecommerce", "site:e-commerce",
            "site:social", "site:blog", "site:portal", "site:generic",
            // UI patterns - too generic
            "ui:button", "ui:form", "ui:input", "ui:modal", "ui:dialog",
            "ui:popup", "ui:overlay", "ui:navigation", "ui:menu",
            // Page patterns - generic
            "page:load", "page:navigation", "page:dynamic", "page:spa",
            // Action patterns - common knowledge
            "action:click", "action:type", "action:scroll", "action:wait",
            "action:navigate", "action:fill", "action:submit",
            // Consent patterns - already handled by system
            "consent:cookie", "consent:gdpr", "consent:banner", "consent:popup",
            // Generic patterns
            "pattern:common", "pattern:standard", "pattern:typical", "pattern:generic",
            "best_practice:general", "best_practice:common", "tip:general"
    );

    // Tag prefixes that are almost always useless (block entire categories)
    private static final java.util.Set<String> BLOCKED_TAG_PREFIXES = java.util.Set.of(
            "site:",      // Site categorizations rarely help
            "ui:",        // Generic UI element types
            "action:",    // Basic action descriptions
            "consent:",   // Consent is handled automatically
            "timing:"     // Timing tips are too vague
    );

    // Brand names that should be anonymized (case-insensitive matching)
    private static final java.util.regex.Pattern BRAND_PATTERN = java.util.regex.Pattern.compile(
            "(?i)\\b(cnn|google|amazon|facebook|meta|twitter|reddit|youtube|netflix|" +
            "microsoft|apple|walmart|ebay|linkedin|instagram|tiktok|spotify|uber|airbnb)\\b"
    );

    // Internal references that are session-specific and useless for future runs
    private static final java.util.regex.Pattern INTERNAL_REF_PATTERN = java.util.regex.Pattern.compile(
            "@e\\d+|ref_\\d+|element_\\d+|#[a-f0-9]{8,}"
    );

    // Useless insight phrases that indicate generic/unhelpful content
    // These are overly vague platitudes that provide no specific actionable guidance
    private static final java.util.regex.Pattern USELESS_PHRASE_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(wait for (the )?page to load|wait for (the )?element|" +
            "click (the|on|a) button|ensure.*is visible|make sure.*appears|" +
            "always.*before|remember to|it.?s important to|best practice is to|" +
            "generally speaking|in most cases|typically you should|usually works|" +
            "common approach|should work|try to|you need to|you should)"
    );

    /**
     * Validates an insight before storing.
     *
     * <p>Filters out:
     * <ul>
     *   <li>Missing or malformed tags</li>
     *   <li>Generic/useless tags (blocklist and prefix-based)</li>
     *   <li>Too short insights (< 100 chars)</li>
     *   <li>Insights containing internal references (@e14, etc.)</li>
     *   <li>Insights containing brand names (should be anonymized)</li>
     *   <li>Insights with useless generic phrases</li>
     * </ul>
     */
    private boolean isValidInsight(InsightDto insight) {
        if (insight.contextTag == null || insight.contextTag.isBlank()) {
            return false;
        }
        if (insight.insightText == null || insight.insightText.isBlank()) {
            return false;
        }

        // Must follow taxonomy format (category:subcategory)
        if (!insight.contextTag.contains(":")) {
            return false;
        }

        // Normalize tag for blocklist check
        String normalizedTag = normalizeTag(insight.contextTag);

        // Reject blocked generic tags (exact match)
        if (BLOCKED_TAGS.contains(normalizedTag)) {
            log.debug("[LIBRARIAN] Rejecting blocked generic tag: {}", normalizedTag);
            return false;
        }

        // Reject entire useless tag categories (prefix match)
        for (String prefix : BLOCKED_TAG_PREFIXES) {
            if (normalizedTag.startsWith(prefix)) {
                log.debug("[LIBRARIAN] Rejecting blocked tag prefix: {} (tag: {})", prefix, normalizedTag);
                return false;
            }
        }

        // Minimum length to ensure meaningful content
        if (insight.insightText.length() < MIN_INSIGHT_LENGTH) {
            log.debug("[LIBRARIAN] Rejecting too-short insight ({} chars): {}",
                    insight.insightText.length(), insight.contextTag);
            return false;
        }

        // Maximum length check
        if (insight.insightText.length() > 500) {
            return false;
        }

        // Reject insights containing internal references (useless for future runs)
        if (INTERNAL_REF_PATTERN.matcher(insight.insightText).find()) {
            log.debug("[LIBRARIAN] Rejecting insight with internal refs: {}", insight.contextTag);
            return false;
        }

        // Reject insights containing brand names (should have been anonymized)
        if (BRAND_PATTERN.matcher(insight.insightText).find()) {
            log.debug("[LIBRARIAN] Rejecting insight with brand names: {}", insight.contextTag);
            return false;
        }

        // Reject insights with useless generic phrases
        if (USELESS_PHRASE_PATTERN.matcher(insight.insightText).find()) {
            log.debug("[LIBRARIAN] Rejecting insight with useless phrases: {}", insight.contextTag);
            return false;
        }

        return true;
    }

    /**
     * Normalizes a tag to consistent format for comparison.
     * Converts to lowercase, replaces hyphens with underscores.
     */
    private String normalizeTag(String tag) {
        if (tag == null) return "";
        return tag.toLowerCase()
                .replace("-", "_")
                .replaceAll("_+", "_");
    }

    /**
     * Sanitizes a context tag for storage.
     * Normalizes format and removes invalid characters.
     */
    private String sanitizeTag(String tag) {
        if (tag == null) return "unknown:unknown";
        // Normalize: lowercase, convert hyphens to underscores, remove invalid chars
        String sanitized = tag.toLowerCase()
                .replace("-", "_")
                .replaceAll("[^a-z0-9:_]", "")
                .replaceAll("_+", "_");
        // Limit length
        return sanitized.substring(0, Math.min(sanitized.length(), 50));
    }

    /**
     * Sanitizes text to remove potential PII and brand names.
     */
    private String sanitizeText(String text) {
        if (text == null) return "";

        String sanitized = text
                // Remove email addresses
                .replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "[EMAIL]")
                // Remove potential passwords
                .replaceAll("password[=:][^\\s&]+", "password=[REDACTED]")
                // Remove API keys (common patterns)
                .replaceAll("(api[_-]?key|token|secret)[=:][^\\s&]+", "$1=[REDACTED]")
                // Remove phone numbers
                .replaceAll("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "[PHONE]")
                // Remove internal references
                .replaceAll("@e\\d+", "[element]")
                .replaceAll("ref_\\d+", "[ref]");

        // Anonymize brand names
        sanitized = BRAND_PATTERN.matcher(sanitized).replaceAll("this site");

        // Limit length
        return sanitized.substring(0, Math.min(sanitized.length(), 500));
    }

    /**
     * Sanitizes URL to remove sensitive query params.
     */
    private String sanitizeUrl(String url) {
        if (url == null) return "";

        // Remove query params that might contain sensitive data
        int queryStart = url.indexOf('?');
        if (queryStart > 0) {
            return url.substring(0, queryStart);
        }
        return url;
    }

    /**
     * Extracted insight ready for storage.
     */
    public record ExtractedInsight(String contextTag, String insightText) {}

    /**
     * DTO for parsing AI response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LibrarianResponse {
        public List<InsightDto> insights;
        public List<SitePatternDto> sitePatterns;
        public String frameworkDetected;
        public String learningConfidence;
    }

    /**
     * DTO for individual insight.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InsightDto {
        public String contextTag;
        public String insightText;
    }

    /**
     * DTO for site pattern from AI response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SitePatternDto {
        public String patternType;
        public String key;
        public String selector;
        public String selectorType;
        public String value;
        public String description;
        public List<AlternativeSelectorDto> alternatives;
    }

    /**
     * DTO for alternative selector from AI response.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class AlternativeSelectorDto {
        public String selector;
        public String selectorType;
    }

    /**
     * Combined extraction result with insights and patterns.
     */
    public record ExtractionResult(
            List<ExtractedInsight> insights,
            List<ExtractedPattern> patterns,
            Optional<String> frameworkDetected,
            String confidence
    ) {
        public static ExtractionResult empty() {
            return new ExtractionResult(List.of(), List.of(), Optional.empty(), "low");
        }
    }

    /**
     * Extracted structured pattern.
     */
    public record ExtractedPattern(
            String domain,
            PatternType type,
            String key,
            String value,
            SelectorType selectorType,
            Optional<String> description,
            List<AlternativeSelector> alternatives
    ) {}

    /**
     * Alternative selector for a pattern.
     */
    public record AlternativeSelector(
            String selector,
            SelectorType type
    ) {}
}
