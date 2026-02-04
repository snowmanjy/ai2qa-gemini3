package com.ai2qa.domain;

/**
 * System-wide constants for the AI2QA platform.
 *
 * <p>IMPORTANT: When adding new personas, update EXPECTED_PERSONA_COUNT here FIRST.
 * Tests will fail if the count doesn't match the actual TestPersona enum values,
 * reminding you to add the enum value as well.
 */
public final class SystemConstants {

    private SystemConstants() {
        // Utility class - no instantiation
    }

    /**
     * The expected number of personas in the system.
     *
     * <p>UPDATE THIS WHEN ADDING NEW PERSONAS.
     * This forces a two-step process:
     * <ol>
     *   <li>Update this count</li>
     *   <li>Add the enum value to TestPersona</li>
     * </ol>
     * If you only do step 1, tests will fail reminding you to do step 2.
     *
     * <p>Current personas: STANDARD, CHAOS, HACKER, PERFORMANCE_HAWK
     */
    public static final int EXPECTED_PERSONA_COUNT = 4;
}
