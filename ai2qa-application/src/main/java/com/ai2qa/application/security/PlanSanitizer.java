package com.ai2qa.application.security;

import com.ai2qa.domain.model.ActionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Validates and sanitizes action steps before execution.
 *
 * <p>
 * Ensures that the agent only performs allowed actions and does not
 * attempt to navigate to unauthorized domains or perform dangerous operations.
 * </p>
 */
@Component
public class PlanSanitizer {

    private static final Logger log = LoggerFactory.getLogger(PlanSanitizer.class);

    private static final int MAX_INPUT_LENGTH = 1200;

    /**
     * Validates a list of action steps.
     *
     * @param steps         The steps to validate
     * @param allowedDomain The domain that navigation is restricted to (e.g.,
     *                      "example.com")
     * @return true if the plan is safe, false otherwise
     */
    public boolean isSafe(List<ActionStep> steps, String allowedDomain) {
        if (steps == null || steps.isEmpty()) {
            return true;
        }

        for (ActionStep step : steps) {
            if (!isStepSafe(step, allowedDomain)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sanitizes a list of action steps by filtering out invalid ones.
     *
     * <p>Instead of failing the entire plan, this method removes steps that:
     * <ul>
     *   <li>Have empty URLs for navigate actions</li>
     *   <li>Have input values exceeding max length</li>
     * </ul>
     *
     * <p>Steps that navigate to unauthorized domains still cause the entire
     * plan to be rejected (handled by {@link #isSafe}).
     *
     * @param steps         The steps to sanitize
     * @param allowedDomain The domain that navigation is restricted to
     * @return A new list with invalid steps removed
     */
    public List<ActionStep> sanitize(List<ActionStep> steps, String allowedDomain) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        List<ActionStep> sanitized = new ArrayList<>();
        int removedCount = 0;

        for (ActionStep step : steps) {
            if (isStepRecoverable(step)) {
                sanitized.add(step);
            } else {
                removedCount++;
                log.warn("Removed invalid step: action='{}', target='{}'",
                        step.action(), step.target());
            }
        }

        if (removedCount > 0) {
            log.info("Sanitized plan: removed {} invalid steps, {} steps remaining",
                    removedCount, sanitized.size());
        }

        return sanitized;
    }

    /**
     * Checks if a step can be recovered/kept in the plan.
     * This is less strict than isStepSafe - it only filters out malformed steps,
     * not security violations which should still fail the whole plan.
     */
    private boolean isStepRecoverable(ActionStep step) {
        String value = step.value().orElse("");
        return switch (step.action()) {
            case "navigate" -> !value.isBlank(); // Filter empty URLs
            case "type" -> value.length() <= MAX_INPUT_LENGTH; // Filter oversized inputs
            default -> true;
        };
    }

    private boolean isStepSafe(ActionStep step, String allowedDomain) {
        String value = step.value().orElse("");
        return switch (step.action()) {
            case "navigate" -> isNavigationSafe(value, allowedDomain);
            case "type" -> isInputSafe(value);
            // "click", "wait", "screenshot" are generally safe if selector is valid
            // (selector validation omitted for now)
            default -> true;
        };
    }

    private boolean isNavigationSafe(String url, String allowedDomain) {
        if (url == null || url.isBlank()) {
            log.warn("Unsafe navigation: Empty URL");
            return false;
        }

        HostResolution targetHost = resolveHost(url);
        if (targetHost.relative()) {
            return true;
        }
        if (targetHost.host().isEmpty()) {
            log.warn("Unsafe navigation: Invalid URL format '{}'", url);
            return false;
        }

        Optional<String> allowedHost = resolveHost(allowedDomain).host();
        if (allowedHost.isEmpty()) {
            return true;
        }

        String target = targetHost.host().get();
        String allowed = allowedHost.get();
        if (!target.endsWith(allowed)) {
            log.warn("Unsafe navigation: URL '{}' does not match allowed domain '{}'", url, allowedDomain);
            return false;
        }

        return true;
    }

    private HostResolution resolveHost(String value) {
        if (value == null || value.isBlank()) {
            return HostResolution.empty(false);
        }

        String trimmed = value.trim();
        if (isRelativeUrl(trimmed)) {
            return HostResolution.relativeHost();
        }

        String normalized = trimmed.contains("://") ? trimmed : "https://" + trimmed;

        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return HostResolution.empty(false);
            }
            return HostResolution.of(normalizeHost(host));
        } catch (IllegalArgumentException e) {
            return HostResolution.empty(false);
        }
    }

    private boolean isRelativeUrl(String value) {
        return value.startsWith("/") || value.startsWith("./") || value.startsWith("../")
                || (!value.contains("://") && !value.contains("."));
    }

    private String normalizeHost(String host) {
        return host.toLowerCase().trim();
    }

    private record HostResolution(Optional<String> host, boolean relative) {
        static HostResolution of(String host) {
            return new HostResolution(Optional.of(host), false);
        }

        static HostResolution relativeHost() {
            return new HostResolution(Optional.empty(), true);
        }

        static HostResolution empty(boolean relative) {
            return new HostResolution(Optional.empty(), relative);
        }
    }

    private boolean isInputSafe(String value) {
        if (value != null && value.length() > MAX_INPUT_LENGTH) {
            log.warn("Unsafe input: Value exceeds max length of {}", MAX_INPUT_LENGTH);
            return false;
        }
        return true;
    }

    /**
     * Validates that the total prompt size (system prompt + user goals) does not
     * exceed the limit.
     * Prevents "Denial of Wallet" attacks via massive token usage.
     *
     * @param systemPrompt The system prompt
     * @param userGoals    The user's list of goals
     * @throws IllegalStateException if the limit is exceeded
     */
    public void validatePromptSize(String systemPrompt, List<String> userGoals) {
        int totalChars = (systemPrompt != null ? systemPrompt.length() : 0);
        if (userGoals != null) {
            for (String goal : userGoals) {
                totalChars += (goal != null ? goal.length() : 0);
            }
        }

        if (totalChars > 15000) {
            log.warn("Prompt size validation failed: Total chars {} > 15000", totalChars);
            throw new IllegalStateException(
                    "Total prompt size " + totalChars + " exceeds limit of 15000 characters");
        }
    }
}
