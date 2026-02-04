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
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Detects blocking UI obstacles (modals, popups, cookie banners, legal agreements)
 * that prevent interaction with the main page content.
 *
 * <p>This service enables proactive obstacle handling - before executing any step,
 * the orchestrator can check for blocking overlays and dismiss them first.
 *
 * <p>This solves the "human QA would just click the Agree button" problem - the AI
 * now perceives and handles ANY blocking element, not just those it planned for.
 */
@Service
public class ObstacleDetector {

    private static final Logger log = LoggerFactory.getLogger(ObstacleDetector.class);
    private static final int MAX_DOM_LENGTH = 15000;
    private static final int MAX_CONSENT_EXTRACT_LENGTH = 5000;

    // Patterns that indicate consent/obstacle content (case-insensitive)
    // CNN uses OneTrust, SourcePoint (sp_message), and WBD consent managers
    private static final java.util.regex.Pattern CONSENT_SECTION_PATTERN = java.util.regex.Pattern.compile(
            "(?i)(IFRAME CONTENT|CONSENT|cookie|onetrust|privacy.?banner|gdpr|" +
            "sp_message|fc-consent|legal.?agreement|terms.?of.?service|" +
            "\"Agree\"|\"Accept\"|accept.*button|agree.*button|" +
            "wbd|warner|cmp-|privacy-?manager|truste|evidon)"  // Added more consent manager patterns
    );

    // Quick check patterns to see if DOM has ANY consent-related content
    private static final java.util.regex.Pattern QUICK_CONSENT_CHECK = java.util.regex.Pattern.compile(
            "(?i)(accept|agree|consent|cookie|privacy|gdpr|onetrust|sp_message)"
    );

    private final ChatClientPort chatClient;
    private final ObjectMapper objectMapper;
    private final PromptSanitizer promptSanitizer;
    private final String systemPrompt;

    public ObstacleDetector(
            @Qualifier("plannerChatPort") ChatClientPort chatClient,
            ObjectMapper objectMapper,
            PromptSanitizer promptSanitizer,
            @Value("classpath:prompts/obstacle-detector/system.md") Resource systemPromptResource) throws IOException {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.promptSanitizer = promptSanitizer;
        this.systemPrompt = systemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * Detects if there's a blocking obstacle on the current page.
     *
     * @param snapshot Current DOM snapshot
     * @return Detection result with obstacle info and dismiss selector, or empty if no obstacle
     */
    public Optional<ObstacleInfo> detect(DomSnapshot snapshot) {
        if (snapshot == null || snapshot.content() == null || snapshot.content().isBlank()) {
            log.debug("[OBSTACLE] Skipping detection - snapshot is null or empty");
            return Optional.empty();
        }

        try {
            // Quick diagnostic: does the DOM contain ANY consent-related keywords?
            boolean hasConsentKeywords = QUICK_CONSENT_CHECK.matcher(snapshot.content()).find();
            log.info("[OBSTACLE] Scanning page (DOM: {} chars, URL: {}, consent keywords present: {})",
                    snapshot.content().length(), snapshot.url(), hasConsentKeywords);

            // If no consent keywords found, log a sample of the DOM for debugging
            if (!hasConsentKeywords) {
                String domSample = snapshot.content().length() > 1000
                        ? snapshot.content().substring(0, 1000) + "..."
                        : snapshot.content();
                log.debug("[OBSTACLE] DOM sample (no consent keywords): {}", domSample);
            }

            String userPrompt = buildUserPrompt(snapshot);

            String response = chatClient.call(systemPrompt, userPrompt, 0.1); // Low temperature for consistency

            // Log AI response for debugging (truncate if too long)
            String responsePreview = response != null
                    ? (response.length() > 500 ? response.substring(0, 500) + "..." : response)
                    : "null";
            log.info("[OBSTACLE] AI response ({} chars): {}",
                    response != null ? response.length() : 0, responsePreview);

            return parseResponse(response);

        } catch (Exception e) {
            log.error("[OBSTACLE] Detection FAILED - AI call error: {}. Consent dialogs may not be dismissed!",
                    e.getMessage(), e);
            return Optional.empty();
        }
    }

    private String buildUserPrompt(DomSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        sb.append("## Current Page\n\n");
        sb.append("**URL:** ").append(snapshot.url()).append("\n");
        sb.append("**Title:** ").append(snapshot.title()).append("\n\n");

        // Extract consent-related sections from RAW DOM BEFORE sanitization/truncation
        // This ensures we capture consent buttons even if they appear late in the DOM
        String consentExtract = extractConsentSections(snapshot.content());

        // Now sanitize (which may truncate to 50k chars)
        String sanitizedDom = promptSanitizer.sanitizeText(snapshot.content());

        // If we found consent content that would be truncated, prepend it
        if (!consentExtract.isEmpty() && sanitizedDom.length() > MAX_DOM_LENGTH) {
            sb.append("## CONSENT/OVERLAY CONTENT (Extracted from page)\n\n");
            sb.append("```\n");
            sb.append(consentExtract);
            sb.append("\n```\n\n");
            log.info("[OBSTACLE] Extracted {} chars of consent content (DOM was {} chars, truncated to {})",
                    consentExtract.length(), sanitizedDom.length(), MAX_DOM_LENGTH);
        } else if (consentExtract.isEmpty() && sanitizedDom.length() > MAX_DOM_LENGTH) {
            log.warn("[OBSTACLE] DOM truncated from {} to {} chars but NO consent patterns found!",
                    sanitizedDom.length(), MAX_DOM_LENGTH);
        }

        sb.append("## DOM Content\n\n");
        sb.append("```html\n");

        // Truncate DOM content (consent content already extracted above)
        if (sanitizedDom.length() > MAX_DOM_LENGTH) {
            sanitizedDom = sanitizedDom.substring(0, MAX_DOM_LENGTH) + "\n... (truncated)";
        }
        sb.append(sanitizedDom);

        sb.append("\n```\n\n");
        sb.append("Analyze this page and detect any blocking obstacles (popups, modals, cookie banners, legal agreements).\n");
        sb.append("If an obstacle exists, provide the best selector to dismiss it.\n");
        sb.append("Pay special attention to the CONSENT/OVERLAY CONTENT section if present - it contains iframe/overlay elements that may be blocking.\n");

        return sb.toString();
    }

    /**
     * Extracts consent-related sections from the DOM that might be missed by truncation.
     * Looks for patterns like IFRAME CONTENT, cookie banners, consent dialogs, etc.
     *
     * <p>This runs on RAW DOM before sanitization to capture consent buttons that may
     * appear late in the DOM (beyond the 50k sanitization limit).
     *
     * @param dom The raw DOM content (before sanitization)
     * @return Extracted and sanitized consent sections, or empty string if none found
     */
    private String extractConsentSections(String dom) {
        return Optional.ofNullable(dom)
                .filter(d -> d.length() > MAX_DOM_LENGTH)
                .map(this::findConsentLines)
                .map(promptSanitizer::sanitizeText)  // Sanitize extracted content for security
                .orElse("");
    }

    private String findConsentLines(String dom) {
        return Arrays.stream(dom.split("\n"))
                .filter(line -> CONSENT_SECTION_PATTERN.matcher(line).find())
                .limit(50)
                .collect(Collectors.collectingAndThen(
                        Collectors.joining("\n"),
                        result -> truncateToLimit(result, MAX_CONSENT_EXTRACT_LENGTH)
                ));
    }

    private String truncateToLimit(String content, int maxLength) {
        return content.length() <= maxLength
                ? content
                : content.substring(0, maxLength);
    }

    // jQuery-style pseudo-selectors that are NOT valid CSS
    private static final java.util.regex.Pattern INVALID_JQUERY_SELECTOR = java.util.regex.Pattern.compile(
            ":contains\\(|:has\\(|:first(?!-)|:last(?!-)|:eq\\(|:gt\\(|:lt\\(|:even|:odd"
    );

    // Pattern to extract text from :contains('text') or :contains("text")
    private static final java.util.regex.Pattern CONTAINS_PATTERN = java.util.regex.Pattern.compile(
            ":contains\\(['\"]([^'\"]+)['\"]\\)"
    );

    private Optional<ObstacleInfo> parseResponse(String response) {
        try {
            String json = extractJson(response);
            ObstacleResponse parsed = objectMapper.readValue(json, ObstacleResponse.class);

            if (!parsed.obstacleDetected) {
                log.info("[OBSTACLE] AI says: NO obstacle detected on this page");
                return Optional.empty();
            }

            // Validate the response
            if (parsed.dismissSelector == null || parsed.dismissSelector.isBlank()) {
                log.warn("[OBSTACLE] Obstacle detected but no dismiss selector provided");
                return Optional.empty();
            }

            // Sanitize selector if it contains invalid jQuery-style patterns
            String sanitizedSelector = sanitizeSelector(parsed.dismissSelector, parsed.dismissText);

            ObstacleInfo info = new ObstacleInfo(
                    parsed.obstacleType,
                    parsed.description,
                    sanitizedSelector,
                    parsed.dismissText,
                    Confidence.fromString(parsed.confidence)
            );

            log.info("[OBSTACLE] Detected {} - dismiss via: {} ({})",
                    info.obstacleType(), info.dismissText(), info.confidence());

            return Optional.of(info);

        } catch (JsonProcessingException e) {
            log.warn("[OBSTACLE] Failed to parse response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Sanitizes a selector by converting jQuery-style patterns to valid CSS.
     * Falls back to text-based matching via aria-label if conversion isn't possible.
     */
    private String sanitizeSelector(String selector, String dismissText) {
        if (selector == null || selector.isBlank()) {
            return selector;
        }

        // Check if selector uses invalid jQuery patterns
        if (!INVALID_JQUERY_SELECTOR.matcher(selector).find()) {
            return selector; // Already valid CSS
        }

        log.warn("[OBSTACLE] Converting jQuery-style selector: {}", selector);

        // Try to extract element type (button, a, div, etc.) before :contains
        String elementType = "button"; // Default
        int colonIndex = selector.indexOf(':');
        if (colonIndex > 0) {
            elementType = selector.substring(0, colonIndex).trim();
            if (elementType.isEmpty()) {
                elementType = "button";
            }
        }

        // Try to extract text from :contains() and use it for aria-label matching
        java.util.regex.Matcher matcher = CONTAINS_PATTERN.matcher(selector);
        String textContent = dismissText; // Fallback to dismissText
        if (matcher.find()) {
            textContent = matcher.group(1);
        }

        // Convert to aria-label selector (more reliable than text content matching)
        // Format: button[aria-label*="Agree"], button[aria-label*="Accept"], etc.
        String sanitized = String.format("%s[aria-label*=\"%s\"]", elementType, textContent);
        log.info("[OBSTACLE] Sanitized selector: {} -> {}", selector, sanitized);

        return sanitized;
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

    /**
     * Information about a detected obstacle.
     */
    public record ObstacleInfo(
            String obstacleType,
            String description,
            String dismissSelector,
            String dismissText,
            Confidence confidence
    ) {}

    /**
     * Confidence level for obstacle detection.
     */
    public enum Confidence {
        HIGH, MEDIUM, LOW;

        public static Confidence fromString(String value) {
            if (value == null) return MEDIUM;
            return switch (value.toLowerCase()) {
                case "high" -> HIGH;
                case "low" -> LOW;
                default -> MEDIUM;
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ObstacleResponse {
        public boolean obstacleDetected;
        public String obstacleType;
        public String description;
        public String dismissSelector;
        public String dismissText;
        public String confidence;
    }
}
