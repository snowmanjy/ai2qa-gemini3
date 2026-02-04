package com.ai2qa.domain.model.knowledge;

/**
 * Types of patterns that can be stored in the knowledge base.
 */
public enum PatternType {
    /**
     * CSS/XPath/ARIA selectors for finding elements.
     */
    SELECTOR,

    /**
     * Timing recommendations (wait times, polling intervals).
     */
    TIMING,

    /**
     * Authentication-related patterns (login flows, session handling).
     */
    AUTH,

    /**
     * Site-specific quirks and workarounds.
     */
    QUIRK
}
