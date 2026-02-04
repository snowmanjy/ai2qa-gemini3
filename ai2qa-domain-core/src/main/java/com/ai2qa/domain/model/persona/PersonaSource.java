package com.ai2qa.domain.model.persona;

/**
 * Source of a persona definition.
 *
 * <ul>
 *   <li>BUILTIN - Ships with the platform (e.g., STANDARD, CHAOS, HACKER)</li>
 *   <li>CUSTOM - Created by a user or admin</li>
 *   <li>ABSORBED - Auto-generated from external skill sources</li>
 * </ul>
 */
public enum PersonaSource {
    BUILTIN,
    CUSTOM,
    ABSORBED
}
