package com.ai2qa.application.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for capturing browser console logs and uncaught exceptions ("The Console Spy").
 *
 * <p>Detects hidden JavaScript crashes, hydration errors, and unhandled rejections
 * that might cause a test to fail or behave unexpectedly.
 *
 * <p>Filters out:
 * <ul>
 *   <li>AI thinking patterns accidentally used as selectors</li>
 *   <li>Third-party tracker noise</li>
 *   <li>Duplicate errors</li>
 * </ul>
 */
@Service
public class ConsoleSpyService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSpyService.class);

    /**
     * Patterns that indicate AI thinking was accidentally used as a selector.
     * These errors are not useful for users and should be filtered out.
     */
    private static final java.util.regex.Pattern AI_THINKING_PATTERN = java.util.regex.Pattern.compile(
            "(?i)" +
            "(I need to|Let me|Looking at|The snapshot|I see|I don't see|" +
            "I can see|Based on|However|appears to be|I'll|I will|" +
            "analyzing|visible elements|section \\[s\\d+|" +
            ":contains\\(['\"]I )" // AI thinking starting with "I " in :contains
    );

    /**
     * Pattern for querySelector errors that contain AI thinking.
     */
    private static final java.util.regex.Pattern SELECTOR_ERROR_WITH_THINKING = java.util.regex.Pattern.compile(
            "(?i)querySelector.*'[^']{50,}" // Long string in selector = likely AI thinking
    );

    /**
     * Internal collector to hold errors during a step execution.
     */
    public static class ErrorCollector {
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private final java.util.Set<String> seenErrorSignatures = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private volatile boolean capturing = true;

        /**
         * Records a console error message.
         */
        public void recordConsoleError(String text) {
            if (capturing && text != null) {
                // Filter out noise and AI thinking patterns
                if (!isNoise(text) && !isAiThinking(text)) {
                    String msg = "[JS Console] " + truncate(text);
                    // Deduplicate by signature (first 100 chars)
                    String signature = extractSignature(msg);
                    if (seenErrorSignatures.add(signature)) {
                        errors.add(msg);
                        log.debug("Captured {}", msg);
                    }
                }
            }
        }

        /**
         * Records an uncaught page exception.
         */
        public void recordPageError(String text) {
            if (capturing && text != null) {
                // Filter out AI thinking patterns that were used as selectors
                if (!isAiThinking(text)) {
                    String msg = "[Uncaught Exception] " + truncate(text);
                    // Deduplicate by signature (first 100 chars)
                    String signature = extractSignature(msg);
                    if (seenErrorSignatures.add(signature)) {
                        errors.add(msg);
                        log.debug("Captured {}", msg);
                    }
                } else {
                    log.debug("Filtered AI thinking from page error: {}...",
                            text.length() > 50 ? text.substring(0, 50) : text);
                }
            }
        }

        public void stop() {
            capturing = false;
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Checks if the error is noise (third-party trackers, etc.).
         */
        private boolean isNoise(String text) {
            if (text == null) return true;
            // Filter out common third-party tracking errors
            return text.contains("googletag") ||
                   text.contains("facebook") ||
                   text.contains("analytics") ||
                   text.contains("gtag") ||
                   text.contains("adsbygoogle");
        }

        /**
         * Checks if the error contains AI thinking that was accidentally used as a selector.
         * This happens when the AI outputs reasoning instead of a valid selector.
         */
        private boolean isAiThinking(String text) {
            if (text == null) return false;
            // Check for querySelector errors with AI thinking
            if (SELECTOR_ERROR_WITH_THINKING.matcher(text).find()) {
                return true;
            }
            // Check for AI thinking patterns
            return AI_THINKING_PATTERN.matcher(text).find();
        }

        /**
         * Extracts a signature for deduplication (first 100 chars).
         */
        private String extractSignature(String text) {
            if (text == null) return "";
            return text.length() > 100 ? text.substring(0, 100) : text;
        }

        private String truncate(String text) {
            if (text == null) return "";
            return text.length() > 500 ? text.substring(0, 500) + "..." : text;
        }
    }

    /**
     * Creates a new collector for capturing console errors.
     *
     * @return A clean collector instance
     */
    public ErrorCollector startCapturing() {
        return new ErrorCollector();
    }

    /**
     * Generates a context string for The Healer based on captured errors.
     */
    public String analyzeForHealer(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }

        StringBuilder analysis = new StringBuilder();
        analysis.append("\n\n[JAVASCRIPT ERRORS DETECTED]\n");
        analysis.append("The browser console reported errors. This often indicates a React/Angular crash or broken component logic:\n\n");

        for (String error : errors) {
            analysis.append("• ").append(error).append("\n");
        }

        if (errors.stream().anyMatch(e -> e.contains("Hydration failed"))) {
            analysis.append("\n⚠️ Hydration Error Detected: This is a Next.js/React SSR mismatch. The DOM changed before JS loaded.\n");
        }
        
        if (errors.stream().anyMatch(e -> e.contains("Cannot read properties of undefined"))) {
            analysis.append("\n⚠️ Null Pointer Exception: The frontend code crashed trying to access a missing object.\n");
        }

        return analysis.toString();
    }
}
