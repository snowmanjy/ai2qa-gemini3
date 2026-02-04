package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.ActionStep;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Factory for ActionStep creation with normalization and defaults.
 */
public final class ActionStepFactory {

    private ActionStepFactory() {
    }

    public static ActionStep of(String action, String target) {
        return new ActionStep(
                generateStepId(),
                safeString(action),
                safeString(target),
                Optional.empty(),
                Optional.empty(),
                Map.of()
        );
    }

    public static ActionStep navigate(String url) {
        String safeUrl = safeString(url);
        return new ActionStep(
                generateStepId(),
                "navigate",
                safeUrl,
                Optional.empty(),
                Optional.of(safeUrl),
                Map.of()
        );
    }

    public static ActionStep click(String target) {
        return new ActionStep(
                generateStepId(),
                "click",
                safeString(target),
                Optional.empty(),
                Optional.empty(),
                Map.of()
        );
    }

    public static ActionStep clickSelector(String target, String selector) {
        return new ActionStep(
                generateStepId(),
                "click",
                safeString(target),
                Optional.ofNullable(selector),
                Optional.empty(),
                Map.of()
        );
    }

    public static ActionStep type(String target, String value) {
        return new ActionStep(
                generateStepId(),
                "type",
                safeString(target),
                Optional.empty(),
                Optional.ofNullable(value),
                Map.of()
        );
    }

    public static ActionStep typeInto(String target, String selector, String value) {
        return new ActionStep(
                generateStepId(),
                "type",
                safeString(target),
                Optional.ofNullable(selector),
                Optional.ofNullable(value),
                Map.of()
        );
    }

    public static ActionStep waitFor(String target, int timeoutMs) {
        return new ActionStep(
                generateStepId(),
                "wait",
                safeString(target),
                Optional.empty(),
                Optional.empty(),
                Map.of("timeout", String.valueOf(timeoutMs))
        );
    }

    public static ActionStep screenshot(String description) {
        return new ActionStep(
                generateStepId(),
                "screenshot",
                safeString(description),
                Optional.empty(),
                Optional.empty(),
                Map.of()
        );
    }

    /**
     * Creates a performance measurement step that captures Core Web Vitals
     * and other performance metrics from the current page.
     *
     * @param description Description of what performance aspect to measure
     * @return ActionStep with "measure_performance" action
     */
    public static ActionStep measurePerformance(String description) {
        return new ActionStep(
                generateStepId(),
                "measure_performance",
                safeString(description),
                Optional.empty(),
                Optional.empty(),
                Map.of("includeResources", "true")
        );
    }

    public static ActionStep withSelector(ActionStep step, String selector) {
        return new ActionStep(
                step.stepId(),
                step.action(),
                step.target(),
                Optional.ofNullable(selector),
                step.value(),
                step.params()
        );
    }

    public static ActionStep reconstitute(
            String stepId,
            String action,
            String target,
            String selector,
            String value,
            Map<String, String> params) {
        return new ActionStep(
                safeString(stepId),
                safeString(action),
                safeString(target),
                Optional.ofNullable(selector),
                Optional.ofNullable(value),
                safeParams(params)
        );
    }

    private static String generateStepId() {
        return "step-" + UUID.randomUUID();
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static Map<String, String> safeParams(Map<String, String> params) {
        return params == null ? Map.of() : Map.copyOf(params);
    }
}
