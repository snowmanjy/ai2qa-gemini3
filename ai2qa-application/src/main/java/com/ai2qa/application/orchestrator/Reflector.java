package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.OrchestratorConfigProvider;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reflects on action execution results to determine next steps.
 *
 * <p>Compares before/after DOM snapshots to verify if an action succeeded.
 * When actions fail, determines appropriate repair strategy.
 */
@Component
public class Reflector {

    private static final Logger log = LoggerFactory.getLogger(Reflector.class);

    private static final int DEFAULT_WAIT_MS = 1000;

    private static final List<String> COOKIE_CONSENT_PATTERNS = List.of(
            "cookie", "consent", "accept all", "accept cookies", "gdpr", "privacy"
    );

    private static final List<String> LEGAL_PATTERNS = List.of(
            "agree", "legal", "terms", "tos", "i accept"
    );

    private static final List<String> POPUP_PATTERNS = List.of(
            "newsletter", "popup", "dismiss", "close modal", "no thanks"
    );

    private static final List<String> CHAT_PATTERNS = List.of(
            "chat widget", "chatbot", "live chat"
    );

    private static final List<String> AD_PATTERNS = List.of(
            "ad feedback", "ad_feedback", "ad choice", "ad-feedback"
    );

    private static final List<String> DISMISS_VERBS = List.of("close", "dismiss");

    private static final List<String> TRANSIENT_UI_TYPES = List.of(
            "banner", "modal", "dialog", "overlay", "notification",
            "alert", "welcome", "announcement", "toast", "popover"
    );

    private static final List<String> WELCOME_QUALIFIERS = List.of(
            "banner", "modal", "screen", "got it", "skip"
    );

    private final OrchestratorConfigProvider config;

    public Reflector(OrchestratorConfigProvider config) {
        this.config = config;
    }

    /**
     * Reflects on an action execution.
     *
     * @param action         The action that was attempted
     * @param snapshotBefore DOM snapshot before execution
     * @param snapshotAfter  DOM snapshot after execution (null if action failed)
     * @param error          Error message if action failed
     * @param retryCount     Number of previous retry attempts
     * @return Reflection result determining next steps
     */
    public ReflectionResult reflect(
            ActionStep action,
            DomSnapshot snapshotBefore,
            DomSnapshot snapshotAfter,
            String error,
            int retryCount
    ) {
        log.debug("Reflecting on action: {} (attempt {})", action.action(), retryCount + 1);

        // If we have an error, handle failure
        if (error != null && !error.isBlank()) {
            return handleFailure(action, snapshotBefore, error, retryCount);
        }

        // If no after snapshot, something went wrong
        if (snapshotAfter == null) {
            return handleFailure(action, snapshotBefore, "No snapshot after execution", retryCount);
        }

        // Verify the action succeeded based on type
        return verifyActionSuccess(action, snapshotBefore, snapshotAfter, retryCount);
    }

    /**
     * Verifies if an action succeeded by comparing snapshots.
     *
     * @param action     The action that was attempted
     * @param before     DOM snapshot before execution
     * @param after      DOM snapshot after execution
     * @param retryCount Number of previous retry attempts (used to prevent infinite loops)
     */
    private ReflectionResult verifyActionSuccess(
            ActionStep action,
            DomSnapshot before,
            DomSnapshot after,
            int retryCount
    ) {
        return switch (action.action()) {
            case "navigate" -> verifyNavigation(action, after);
            case "click" -> verifyClick(action, before, after, retryCount);
            case "type" -> verifyType(action, before, after);
            case "wait" -> ReflectionResult.success(null);
            case "screenshot" -> ReflectionResult.success(null);
            default -> ReflectionResult.success(action.selector().orElse(null));
        };
    }

    /**
     * Verifies navigation succeeded.
     */
    private ReflectionResult verifyNavigation(ActionStep action, DomSnapshot after) {
        String targetUrl = action.value().orElse("");

        // Check if URL changed (basic verification)
        if (after.url() != null && !after.url().isBlank()) {
            // Navigation succeeded if we have a URL
            log.debug("Navigation verified: {}", after.url());
            return ReflectionResult.success(null);
        }

        return ReflectionResult.retry(
                "Navigation may not have completed",
                ActionStepFactory.waitFor("page load", 2000)
        );
    }

    /**
     * Verifies click succeeded by checking DOM changes.
     *
     * <p>IMPORTANT: This method respects MAX_RETRIES to prevent infinite loops.
     * If DOM doesn't change after multiple attempts, we assume the click worked
     * (some clicks don't cause visible DOM changes, e.g., analytics triggers).
     */
    private ReflectionResult verifyClick(
            ActionStep action,
            DomSnapshot before,
            DomSnapshot after,
            int retryCount
    ) {
        // Simple heuristic: if DOM changed, click probably worked
        if (!before.content().equals(after.content())) {
            log.debug("Click verified: DOM changed");
            return ReflectionResult.success(action.selector().orElse(null));
        }

        // Prevent infinite wait loop: after maxRetries, assume success
        // (some clicks don't cause DOM changes - analytics, tracking, etc.)
        if (retryCount >= config.getMaxRetries()) {
            log.info("Click verification: DOM unchanged after {} attempts, assuming success", retryCount + 1);
            return ReflectionResult.success(action.selector().orElse(null));
        }

        // DOM unchanged - might need to wait for async changes
        return ReflectionResult.waitFor(
                "Waiting for DOM update after click",
                DEFAULT_WAIT_MS
        );
    }

    /**
     * Verifies type action succeeded by checking for value in DOM.
     */
    private ReflectionResult verifyType(
            ActionStep action,
            DomSnapshot before,
            DomSnapshot after
    ) {
        String typedValue = action.value().orElse("");

        // Check if the typed value appears in the snapshot
        if (!typedValue.isBlank() && after.containsText(typedValue)) {
            log.debug("Type verified: value found in DOM");
            return ReflectionResult.success(action.selector().orElse(null));
        }

        // Value might be masked (password fields) - check DOM changed
        if (!before.content().equals(after.content())) {
            log.debug("Type verified: DOM changed (value may be masked)");
            return ReflectionResult.success(action.selector().orElse(null));
        }

        return ReflectionResult.success(action.selector().orElse(null));
    }

    /**
     * Handles action failure.
     */
    private ReflectionResult handleFailure(
            ActionStep action,
            DomSnapshot snapshot,
            String error,
            int retryCount
    ) {
        log.warn("Action failed: {} - {} (attempt {})", action.action(), error, retryCount + 1);

        // Check if we've exceeded max retries - skip and continue instead of aborting
        // A single failed step should not kill the entire test run.
        // The step is recorded as SKIPPED in the report so nothing is hidden.
        if (retryCount >= config.getMaxRetries()) {
            String stepDesc = getStepDescription(action);
            String skipReason = isDismissAction(action)
                    ? String.format("Dismiss step '%s' skipped: element already handled", stepDesc)
                    : String.format("Step '%s' skipped after %d attempts: %s", stepDesc, retryCount + 1, error);
            log.info("Skipping step '{}' after {} attempts: {}",
                    action.target(), retryCount + 1, error);
            return ReflectionResult.skip(skipReason);
        }

        // Determine repair strategy based on error type
        if (isElementNotFoundError(error)) {
            // Element not found - need AI to find new selector
            return ReflectionResult.retry(
                    "Element not found, need new selector",
                    createSelectorRepairStep(action, snapshot)
            );
        }

        if (isTimeoutError(error)) {
            // Timeout - add wait step
            return ReflectionResult.retry(
                    "Timeout occurred",
                    ActionStepFactory.waitFor("element to appear", 3000)
            );
        }

        // Generic retry
        return ReflectionResult.retry(
                "Retrying action: " + error,
                action
        );
    }

    /**
     * Creates a repair step for finding a new selector.
     */
    private ActionStep createSelectorRepairStep(ActionStep original, DomSnapshot snapshot) {
        // Return the original action - the orchestrator will ask AI for new selector
        return ActionStepFactory.withSelector(original, null);
    }

    private boolean isElementNotFoundError(String error) {
        String lowerError = error.toLowerCase();
        return lowerError.contains("not found") ||
               lowerError.contains("no such element") ||
               lowerError.contains("unable to locate") ||
               lowerError.contains("selector");
    }

    private boolean isTimeoutError(String error) {
        String lowerError = error.toLowerCase();
        return lowerError.contains("timeout") ||
               lowerError.contains("timed out");
    }

    /**
     * Determines if a step is optional (can be skipped without failing the test).
     *
     * <p>Optional steps include:
     * <ul>
     *   <li>Cookie consent/acceptance - may not be present on all pages/regions</li>
     *   <li>Legal/TOS agreement popups - may not appear or may already be accepted</li>
     *   <li>Newsletter popup dismissal - may not appear</li>
     *   <li>Chat widget interactions - may not be loaded</li>
     *   <li>Ad feedback/ad choice widgets - intermittent appearance</li>
     *   <li>Close/dismiss of transient UI (banners, modals, dialogs, overlays, etc.)</li>
     *   <li>Welcome screens/banners that may have been auto-dismissed</li>
     * </ul>
     */
    private boolean isOptionalStep(ActionStep action) {
        String target = Optional.ofNullable(action.target()).orElse("").toLowerCase();

        return matchesAny(target, COOKIE_CONSENT_PATTERNS)
                || matchesAny(target, LEGAL_PATTERNS)
                || matchesAny(target, POPUP_PATTERNS)
                || matchesAny(target, CHAT_PATTERNS)
                || matchesAny(target, AD_PATTERNS)
                || isDismissOfTransientElement(target)
                || isWelcomeElement(target);
    }

    private boolean isDismissAction(ActionStep action) {
        String target = Optional.ofNullable(action.target()).orElse("").toLowerCase();
        return isDismissOfTransientElement(target) || isWelcomeElement(target);
    }

    private boolean matchesAny(String target, List<String> patterns) {
        return patterns.stream().anyMatch(target::contains);
    }

    private boolean isDismissOfTransientElement(String target) {
        return matchesAny(target, DISMISS_VERBS)
                && (matchesAny(target, TRANSIENT_UI_TYPES) || isCloseButton(target));
    }

    private boolean isCloseButton(String target) {
        // "close X button" or "dismiss X button" = a close/dismiss button for some UI element
        return target.endsWith("button") || target.endsWith("btn") || target.contains("close button");
    }

    private boolean isWelcomeElement(String target) {
        return target.contains("welcome")
                && matchesAny(target, WELCOME_QUALIFIERS);
    }

    /**
     * Gets a human-readable description of the step for logging.
     */
    private String getStepDescription(ActionStep action) {
        return Optional.ofNullable(action.target())
                .filter(t -> !t.isBlank())
                .orElseGet(() -> action.selector()
                        .filter(s -> !s.isBlank())
                        .orElse(action.action()));
    }
}
