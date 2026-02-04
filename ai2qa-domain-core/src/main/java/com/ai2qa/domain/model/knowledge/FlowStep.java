package com.ai2qa.domain.model.knowledge;

import java.util.List;
import java.util.Optional;

/**
 * Represents a single step in a test flow strategy.
 */
public record FlowStep(
        String action,
        String description,
        Optional<String> selector,
        Optional<SelectorType> selectorType,
        Optional<String> inputValue,
        Optional<Integer> waitMs,
        Optional<String> assertion,
        List<String> alternativeSelectors
) {

    /**
     * Creates a simple click step.
     */
    public static FlowStep click(String selector, String description) {
        return new FlowStep(
                "click", description,
                Optional.of(selector), Optional.of(SelectorType.CSS),
                Optional.empty(), Optional.empty(), Optional.empty(),
                List.of()
        );
    }

    /**
     * Creates a fill/type step.
     */
    public static FlowStep fill(String selector, String value, String description) {
        return new FlowStep(
                "fill", description,
                Optional.of(selector), Optional.of(SelectorType.CSS),
                Optional.of(value), Optional.empty(), Optional.empty(),
                List.of()
        );
    }

    /**
     * Creates a navigation step.
     */
    public static FlowStep navigate(String url, String description) {
        return new FlowStep(
                "navigate", description,
                Optional.empty(), Optional.empty(),
                Optional.of(url), Optional.empty(), Optional.empty(),
                List.of()
        );
    }

    /**
     * Creates a wait step.
     */
    public static FlowStep wait(int milliseconds, String description) {
        return new FlowStep(
                "wait", description,
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(milliseconds), Optional.empty(),
                List.of()
        );
    }

    /**
     * Creates an assertion step.
     */
    public static FlowStep assertVisible(String selector, String description) {
        return new FlowStep(
                "assert", description,
                Optional.of(selector), Optional.of(SelectorType.CSS),
                Optional.empty(), Optional.empty(), Optional.of("visible"),
                List.of()
        );
    }
}
