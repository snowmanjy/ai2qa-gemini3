package com.ai2qa.application.port;

/**
 * Configuration info about the active AI provider.
 *
 * @param provider The provider name (e.g., "vertex-ai", "anthropic", "openai")
 * @param model    The model name (e.g., "gemini-2.0-flash", "claude-sonnet-4")
 */
public record AiProviderInfo(
        String provider,
        String model
) {
}
