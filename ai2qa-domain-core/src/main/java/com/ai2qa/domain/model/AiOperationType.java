package com.ai2qa.domain.model;

/**
 * Type of AI operation being performed.
 */
public enum AiOperationType {
    /**
     * Finding an element ref/selector from a page snapshot.
     */
    ELEMENT_FIND,

    /**
     * Generating a test plan from goals.
     */
    PLAN_GENERATION,

    /**
     * Creating a repair/self-healing plan after failure.
     */
    REPAIR_PLAN,

    /**
     * Reflecting on action results.
     */
    REFLECTION
}
