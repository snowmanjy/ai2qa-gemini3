package com.ai2qa.application.port;

/**
 * Port for AI chat interactions.
 */
public interface ChatClientPort {

    /**
     * Executes a chat call with an optional system prompt.
     *
     * @param systemPrompt System prompt to steer behavior (optional)
     * @param userPrompt   User prompt content
     * @return Model response content
     */
    String call(String systemPrompt, String userPrompt);

    /**
     * Executes a chat call with system prompt and custom temperature.
     *
     * <p>Temperature controls randomness of AI responses:
     * <ul>
     *   <li>0.0-0.3: Deterministic, consistent outputs</li>
     *   <li>0.4-0.6: Balanced variation</li>
     *   <li>0.7-1.0: High randomness, creative outputs</li>
     * </ul>
     *
     * @param systemPrompt System prompt to steer behavior (optional)
     * @param userPrompt   User prompt content
     * @param temperature  Temperature override (0.0 to 1.0), null uses default
     * @return Model response content
     */
    default String call(String systemPrompt, String userPrompt, Double temperature) {
        // Default implementation ignores temperature for backward compatibility
        return call(systemPrompt, userPrompt);
    }

    /**
     * Executes a chat call without an explicit system prompt.
     *
     * @param userPrompt User prompt content
     * @return Model response content
     */
    default String call(String userPrompt) {
        return call(null, userPrompt);
    }

    /**
     * Executes a chat call and returns full response with usage metadata.
     *
     * @param systemPrompt System prompt to steer behavior (optional)
     * @param userPrompt   User prompt content
     * @return Full response including content and token usage
     */
    AiResponse callWithMetrics(String systemPrompt, String userPrompt);

    /**
     * Executes a chat call without system prompt and returns full response.
     *
     * @param userPrompt User prompt content
     * @return Full response including content and token usage
     */
    default AiResponse callWithMetrics(String userPrompt) {
        return callWithMetrics(null, userPrompt);
    }

    /**
     * Executes a chat call with custom temperature and returns detailed metrics.
     *
     * @param systemPrompt System prompt to steer behavior (optional)
     * @param userPrompt   User prompt content
     * @param temperature  Temperature override (0.0 to 1.0), null uses default
     * @return AI response with metrics (content, tokens, latency)
     */
    default AiResponse callWithMetrics(String systemPrompt, String userPrompt, Double temperature) {
        // Default implementation ignores temperature for backward compatibility
        return callWithMetrics(systemPrompt, userPrompt);
    }

    /**
     * Executes a chat call with custom timeout.
     *
     * <p>Use this for long-running AI calls like report generation that may need
     * more time than the default timeout (e.g., 180s for summary generation).
     *
     * @param systemPrompt   System prompt to steer behavior (optional)
     * @param userPrompt     User prompt content
     * @param timeoutSeconds Custom timeout in seconds (null uses default)
     * @return Model response content
     */
    default String callWithTimeout(String systemPrompt, String userPrompt, Long timeoutSeconds) {
        // Default implementation ignores timeout for backward compatibility
        return call(systemPrompt, userPrompt);
    }
}
