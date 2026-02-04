package com.ai2qa.application.port;

import java.time.Instant;

/**
 * Port interface for loading AI prompts.
 *
 * <p>Provides abstraction for prompt loading with support for:
 * <ul>
 *   <li>Mode-aware prompts (aria vs legacy)</li>
 *   <li>Hot-reload capability</li>
 *   <li>Graceful fallback when files are missing</li>
 * </ul>
 *
 * <p>Prompts are organized by role (architect, hunter, healer, reporter)
 * and type (system, json-schema).
 */
public interface PromptLoaderPort {

    /**
     * Gets a prompt by agent role, type, and optional mode.
     *
     * @param role Agent role: "architect", "hunter", "healer", "reporter"
     * @param promptType Type of prompt: "system", "json-schema"
     * @param mode Optional mode variant: "aria", "legacy", or null for no mode
     * @return The prompt content, or fallback if not found
     */
    String getPrompt(String role, String promptType, String mode);

    /**
     * Gets a prompt without mode variant.
     *
     * @param role Agent role: "architect", "hunter", "healer", "reporter"
     * @param promptType Type of prompt: "system", "json-schema"
     * @return The prompt content, or fallback if not found
     */
    default String getPrompt(String role, String promptType) {
        return getPrompt(role, promptType, null);
    }

    /**
     * Triggers an immediate reload of all prompts from source files.
     * Clears the cache and re-reads all prompt files.
     */
    void reload();

    /**
     * Gets the timestamp of the last successful reload.
     *
     * @return The instant when prompts were last reloaded
     */
    Instant getLastReloadTime();

    /**
     * Convenience method to get the full Architect prompt (system + schema).
     *
     * @return Combined Architect system prompt and JSON schema
     */
    default String getArchitectPrompt() {
        String system = getPrompt("architect", "system");
        String schema = getPrompt("architect", "json-schema");
        return combinePrompts(system, schema);
    }

    /**
     * Convenience method to get the Hunter prompt for the specified mode.
     *
     * @param useAriaMode true for aria mode, false for legacy mode
     * @return The Hunter system prompt for the specified mode
     */
    default String getHunterPrompt(boolean useAriaMode) {
        String mode = useAriaMode ? "aria" : "legacy";
        return getPrompt("hunter", "system", mode);
    }

    /**
     * Convenience method to get the full Healer prompt (system + schema) for the specified mode.
     *
     * @param useAriaMode true for aria mode, false for legacy mode
     * @return Combined Healer system prompt and JSON schema for the specified mode
     */
    default String getHealerPrompt(boolean useAriaMode) {
        String mode = useAriaMode ? "aria" : "legacy";
        String system = getPrompt("healer", "system", mode);
        String schema = getPrompt("healer", "json-schema", mode);
        return combinePrompts(system, schema);
    }

    /**
     * Convenience method to get the Reporter prompt with the specified schema.
     *
     * @param isSuccess true for success schema, false for failure schema
     * @return Combined Reporter system prompt and appropriate schema
     */
    default String getReporterPrompt(boolean isSuccess) {
        String system = getPrompt("reporter", "system");
        String schema = getPrompt("reporter", isSuccess ? "success-schema" : "failure-schema");
        return combinePrompts(system, schema);
    }

    /**
     * Combines two prompts with proper separator.
     */
    private static String combinePrompts(String system, String schema) {
        if (schema == null || schema.isBlank()) {
            return system;
        }
        return system + "\n\n" + schema;
    }
}
