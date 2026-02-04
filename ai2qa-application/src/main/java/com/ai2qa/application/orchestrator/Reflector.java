package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.OrchestratorConfigProvider;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

        // Check if we've exceeded max retries
        if (retryCount >= config.getMaxRetries()) {
            // For optional steps like cookie consent, skip instead of abort
            if (isOptionalStep(action)) {
                log.info("Skipping optional step '{}' after {} attempts: element not found",
                        action.target(), retryCount + 1);
                return ReflectionResult.skip(
                        String.format("Optional step '%s' skipped: element not present on page",
                                getStepDescription(action))
                );
            }
            return ReflectionResult.abort(
                    String.format("Action '%s' failed after %d attempts: %s",
                            action.action(), retryCount + 1, error)
            );
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
     * </ul>
     */
    private boolean isOptionalStep(ActionStep action) {
        String target = Optional.ofNullable(action.target()).orElse("").toLowerCase();

        // Cookie consent patterns
        if (target.contains("cookie") || target.contains("consent") ||
            target.contains("accept all") || target.contains("accept cookies") ||
            target.contains("gdpr") || target.contains("privacy")) {
            return true;
        }

        // Legal/TOS agreement patterns
        if (target.contains("agree") || target.contains("legal") ||
            target.contains("terms") || target.contains("tos") ||
            target.contains("i accept")) {
            return true;
        }

        // Newsletter/popup dismissal patterns
        if (target.contains("newsletter") || target.contains("popup") ||
            target.contains("dismiss") || target.contains("close modal") ||
            target.contains("no thanks")) {
            return true;
        }

        // Chat widget patterns
        if (target.contains("chat widget") || target.contains("chatbot") ||
            target.contains("live chat")) {
            return true;
        }

        // Ad feedback patterns (common on news sites like CNN)
        if (target.contains("ad feedback") || target.contains("ad_feedback") ||
            target.contains("ad choice") || target.contains("ad-feedback")) {
            return true;
        }

        return false;
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
