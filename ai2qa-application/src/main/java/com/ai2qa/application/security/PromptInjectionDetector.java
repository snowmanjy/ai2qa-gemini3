package com.ai2qa.application.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects potential prompt injection attacks in user input.
 *
 * <p>
 * This is a basic implementation using keyword matching. In a production
 * environment,
 * this should be enhanced with more sophisticated heuristics or a dedicated
 * security model.
 * </p>
 */
@Component
public class PromptInjectionDetector {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionDetector.class);

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget all instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal your instructions", Pattern.CASE_INSENSITIVE));

    /**
     * Checks if the given text contains potential prompt injection attempts.
     *
     * @param text The text to evaluate
     * @return true if injection is detected, false otherwise
     */
    public boolean isSafe(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                log.warn("Potential prompt injection detected: '{}' matches pattern '{}'", text, pattern.pattern());
                return false;
            }
        }

        return true;
    }

    /**
     * Checks a list of texts (e.g., goals) for injection attempts.
     *
     * @param texts List of texts to evaluate
     * @return true if ALL texts are safe, false if ANY text is suspicious
     */
    public boolean areSafe(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return true;
        }

        for (String text : texts) {
            if (!isSafe(text)) {
                return false;
            }
        }

        return true;
    }
}
