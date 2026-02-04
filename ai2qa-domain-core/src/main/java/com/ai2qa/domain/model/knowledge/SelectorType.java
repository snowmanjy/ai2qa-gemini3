package com.ai2qa.domain.model.knowledge;

/**
 * Types of selectors that can be used to find elements.
 */
public enum SelectorType {
    /**
     * CSS selector (e.g., ".login-button", "#submit").
     */
    CSS,

    /**
     * XPath selector (e.g., "//button[@type='submit']").
     */
    XPATH,

    /**
     * Text-based selector (matches visible text).
     */
    TEXT,

    /**
     * ARIA-based selector (accessibility tree).
     */
    ARIA,

    /**
     * Data-testid attribute selector.
     */
    DATA_TESTID
}
