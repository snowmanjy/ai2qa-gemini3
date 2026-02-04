package com.ai2qa.application.port;

/**
 * Response from an AI model call including usage metadata.
 *
 * @param content      The response content
 * @param inputTokens  Tokens sent to the model
 * @param outputTokens Tokens received from the model
 * @param latencyMs    Response time in milliseconds
 * @param modelName    The model that generated this response
 */
public record AiResponse(
        String content,
        int inputTokens,
        int outputTokens,
        long latencyMs,
        String modelName
) {
    /**
     * Creates an AiResponse with just content (for backward compatibility).
     *
     * @param content The text content
     * @return AiResponse with zeros for metrics
     */
    public static AiResponse of(String content) {
        return new AiResponse(content, 0, 0, 0, "unknown");
    }

    /**
     * Creates a response with unknown token counts (for fallback/error cases).
     */
    public static AiResponse withUnknownUsage(String content, long latencyMs) {
        return new AiResponse(content, 0, 0, latencyMs, "unknown");
    }

    /**
     * Total tokens used (input + output).
     */
    public int totalTokens() {
        return inputTokens + outputTokens;
    }
}
