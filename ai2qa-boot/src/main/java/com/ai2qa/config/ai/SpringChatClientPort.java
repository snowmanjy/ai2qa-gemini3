package com.ai2qa.config.ai;

import com.ai2qa.application.port.AiResponse;
import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.port.PromptLoaderPort;
import com.ai2qa.config.BrowserConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Spring AI ChatClient adapter implementing ChatClientPort.
 *
 * <p>Supports hot-reloadable prompts via PromptLoaderPort and temperature override per-call.
 * When role is specified, prompts are loaded at call time (supports hot-reload).
 * When role is null, uses the defaultSystemPrompt (legacy behavior).
 *
 * <p>Includes automatic retry with exponential backoff for rate limit errors (429/RESOURCE_EXHAUSTED).
 * When a fallback ChatClient is configured, it will be used after the primary model exhausts retries
 * due to rate limiting, providing seamless failover between models (e.g., Haiku → Sonnet).
 *
 * <p>CRITICAL: All AI API calls are protected with a 60-second timeout to prevent test runs
 * from hanging forever when the AI provider is slow or unresponsive.
 */
class SpringChatClientPort implements ChatClientPort {

    private static final Logger log = LoggerFactory.getLogger(SpringChatClientPort.class);

    // Retry configuration for rate limit errors
    // Based on GCP guidance: https://cloud.google.com/blog/products/ai-machine-learning/learn-how-to-handle-429-resource-exhaustion-errors-in-your-llms
    // Anthropic rate limit: 50k input tokens/min - need longer backoff to let quota reset
    private static final int MAX_RETRIES = 4;          // More retries with longer backoff
    private static final int BACKOFF_MULTIPLIER = 2;   // Longer initial delays (2s, 4s, 8s, 16s)
    private static final long MAX_DELAY_MS = 60_000;   // 60 seconds max to allow quota reset
    private static final java.util.Random RANDOM = new java.util.Random();

    // Timeout configuration - CRITICAL for preventing stuck tests
    // Increased from 60s to 120s to accommodate slow AI responses during high load
    private static final long AI_CALL_TIMEOUT_SECONDS = 120;  // 120 seconds max for any AI call

    /**
     * Bounded thread pool for AI calls to prevent resource exhaustion.
     *
     * <p>Pool sizing rationale:
     * <ul>
     *   <li>Max concurrent tests: 30 (from ai2qa.concurrent-limit.max-global)</li>
     *   <li>AI calls per test: typically 10-20 during execution</li>
     *   <li>Summary/report calls after completion: 1-2 per test</li>
     *   <li>Set to 20 threads as a reasonable limit for concurrent AI operations</li>
     * </ul>
     *
     * <p>Using a bounded pool instead of cached prevents:
     * <ul>
     *   <li>Unlimited thread creation under load</li>
     *   <li>Thread starvation cascading to other components (e.g., HikariCP)</li>
     *   <li>Memory exhaustion from too many pending AI requests</li>
     * </ul>
     */
    private static final int AI_EXECUTOR_POOL_SIZE = 20;
    private static final ExecutorService TIMEOUT_EXECUTOR = Executors.newFixedThreadPool(
            AI_EXECUTOR_POOL_SIZE,
            r -> {
                Thread t = new Thread(r, "ai-call-thread");
                t.setDaemon(true);  // Don't prevent JVM shutdown
                return t;
            }
    );

    // Graceful shutdown hook for the executor service
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down AI timeout executor...");
            TIMEOUT_EXECUTOR.shutdown();
            try {
                if (!TIMEOUT_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("AI timeout executor did not terminate gracefully, forcing shutdown");
                    TIMEOUT_EXECUTOR.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for AI timeout executor shutdown");
                TIMEOUT_EXECUTOR.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "ai-executor-shutdown"));
    }

    private final ChatClient chatClient;
    private final ChatClient fallbackChatClient;
    private final String defaultSystemPrompt;
    private final String modelName;
    private final String fallbackModelName;
    private final String role;
    private final PromptLoaderPort promptLoader;
    private final BrowserConfiguration browserConfig;

    /**
     * Legacy constructor for backward compatibility.
     */
    SpringChatClientPort(ChatClient chatClient, String defaultSystemPrompt, String modelName) {
        this(chatClient, null, defaultSystemPrompt, modelName, null, null, null, null);
    }

    /**
     * Hot-reload constructor with role-based prompt loading (legacy, no failover).
     */
    SpringChatClientPort(ChatClient chatClient, String defaultSystemPrompt, String modelName,
                         String role, PromptLoaderPort promptLoader, BrowserConfiguration browserConfig) {
        this(chatClient, null, defaultSystemPrompt, modelName, null, role, promptLoader, browserConfig);
    }

    /**
     * Full constructor with model failover support.
     *
     * @param chatClient The primary ChatClient
     * @param fallbackChatClient The fallback ChatClient for rate limit failover (can be null)
     * @param defaultSystemPrompt Fallback system prompt (used when role is null)
     * @param modelName The primary AI model name for metrics
     * @param fallbackModelName The fallback AI model name for metrics (can be null)
     * @param role The agent role for prompt loading: "architect", "hunter", "healer", "reporter"
     * @param promptLoader The prompt loader for hot-reloadable prompts
     * @param browserConfig Browser config for aria mode detection (can be null for non-mode-aware roles)
     */
    SpringChatClientPort(ChatClient chatClient, ChatClient fallbackChatClient, String defaultSystemPrompt,
                         String modelName, String fallbackModelName, String role,
                         PromptLoaderPort promptLoader, BrowserConfiguration browserConfig) {
        this.chatClient = chatClient;
        this.fallbackChatClient = fallbackChatClient;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.modelName = modelName;
        this.fallbackModelName = fallbackModelName;
        this.role = role;
        this.promptLoader = promptLoader;
        this.browserConfig = browserConfig;
    }

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, null);
    }

    @Override
    public String call(String systemPrompt, String userPrompt, Double temperature) {
        return callWithMetrics(systemPrompt, userPrompt, temperature).content();
    }

    @Override
    public AiResponse callWithMetrics(String systemPrompt, String userPrompt) {
        return callWithMetrics(systemPrompt, userPrompt, null);
    }

    @Override
    public AiResponse callWithMetrics(String systemPrompt, String userPrompt, Double temperature) {
        if (userPrompt == null || userPrompt.isBlank()) {
            throw new IllegalArgumentException("User prompt is required");
        }

        // Estimate token count (rough: ~4 chars per token for English)
        int estimatedTokens = (userPrompt.length() + (systemPrompt != null ? systemPrompt.length() : 0)) / 4;
        log.info("[AI CALL START] Role: {}, Model: {}, Prompt: {} chars (~{} tokens), Timeout: {}s",
                role, modelName, userPrompt.length(), estimatedTokens, AI_CALL_TIMEOUT_SECONDS);

        long startTime = System.currentTimeMillis();

        // Try primary model first
        try {
            AiResponse response = executeWithRetries(chatClient, modelName, systemPrompt, userPrompt, temperature, startTime);
            log.info("[AI CALL SUCCESS] Role: {}, Model: {}, Latency: {}ms, Output tokens: {}",
                    role, response.modelName(), response.latencyMs(), response.outputTokens());
            return response;
        } catch (RateLimitException e) {
            log.warn("[AI CALL RATE LIMITED] Role: {}, Model: {}", role, modelName);
            // Primary model exhausted retries due to rate limiting
            if (fallbackChatClient != null) {
                log.warn("Primary model {} exhausted rate limit retries. Failing over to {}",
                        modelName, fallbackModelName);
                try {
                    AiResponse fallbackResponse = executeWithRetries(fallbackChatClient, fallbackModelName,
                            systemPrompt, userPrompt, temperature, startTime);
                    log.info("[AI CALL SUCCESS] Role: {} (fallback), Model: {}, Latency: {}ms",
                            role, fallbackResponse.modelName(), fallbackResponse.latencyMs());
                    return fallbackResponse;
                } catch (RateLimitException fallbackRle) {
                    // Both models hit rate limits - this is a critical error
                    String errorMsg = String.format(
                            "Both primary (%s) and fallback (%s) models exhausted rate limits",
                            modelName, fallbackModelName);
                    log.error("[AI CALL FAILED] Role: {}, Error: {}", role, errorMsg, fallbackRle);
                    throw new RuntimeException(errorMsg, fallbackRle);
                }
            }
            // No fallback available
            log.error("[AI CALL FAILED] Role: {}, Model: {}, Error: Rate limited with no fallback",
                    role, modelName);
            throw new RuntimeException("Primary model rate limited with no fallback configured", e);
        }
    }

    /**
     * Executes the request with retries on a specific chat client.
     *
     * @throws RateLimitException if all retries exhausted due to rate limiting
     * @throws RuntimeException for other errors
     */
    private AiResponse executeWithRetries(ChatClient client, String currentModelName,
                                          String systemPrompt, String userPrompt,
                                          Double temperature, long overallStartTime) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                ChatResponse response = executeRequest(client, systemPrompt, userPrompt, temperature);
                long latencyMs = System.currentTimeMillis() - overallStartTime;

                String content = response.getResult() != null
                        ? response.getResult().getOutput().getContent()
                        : "";

                int inputTokens = 0;
                int outputTokens = 0;

                if (response.getMetadata() != null) {
                    Usage usage = response.getMetadata().getUsage();
                    if (usage != null) {
                        inputTokens = safeToInt(usage.getPromptTokens());
                        outputTokens = safeToInt(usage.getGenerationTokens());
                    }
                }

                if (attempt > 0) {
                    log.info("AI request to {} succeeded after {} retries", currentModelName, attempt);
                }

                return new AiResponse(content, inputTokens, outputTokens, latencyMs, currentModelName);

            } catch (Exception e) {
                lastException = e;

                if (isRateLimitError(e) && attempt < MAX_RETRIES) {
                    // Try to extract Retry-After from error, otherwise use exponential backoff
                    final int currentAttempt = attempt;
                    long delayMs = extractRetryAfterMs(e).orElseGet(() -> calculateBackoffDelay(currentAttempt));
                    log.warn("Rate limit hit on {} (attempt {}/{}). Retrying in {}ms. Error: {}",
                            currentModelName, attempt + 1, MAX_RETRIES + 1, delayMs, extractErrorMessage(e));

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("AI request interrupted during retry backoff", ie);
                    }
                    attempt++;
                } else if (isRateLimitError(e)) {
                    // Max retries exceeded for rate limit - throw special exception for failover
                    throw new RateLimitException("Rate limit retries exhausted for " + currentModelName, e);
                } else {
                    // Not a rate limit error - don't failover, just throw
                    String errorMsg = String.format("AI request to %s failed: %s",
                            currentModelName, extractErrorMessage(e));
                    log.error(errorMsg, e);
                    throw new RuntimeException(errorMsg, e);
                }
            }
        }

        // Should not reach here, but just in case
        throw new RateLimitException("Rate limit retries exhausted for " + currentModelName, lastException);
    }

    /**
     * Exception indicating rate limit retries were exhausted.
     * Used internally to trigger failover to the fallback model.
     */
    private static class RateLimitException extends RuntimeException {
        RateLimitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Executes the actual API request on the specified client with timeout protection.
     *
     * <p>CRITICAL: Wraps the blocking AI call with a timeout to prevent
     * test runs from hanging forever when the AI provider is slow or unresponsive.
     *
     * <p>Uses get() with timeout instead of orTimeout().join() to enable proper
     * cancellation of the underlying task when timeout occurs.
     *
     * @throws RuntimeException if the AI call times out or fails
     */
    private ChatResponse executeRequest(ChatClient client, String systemPrompt, String userPrompt, Double temperature) {
        // Build the request first (this is fast, doesn't need timeout)
        ChatClient.ChatClientRequestSpec request = client.prompt();

        // Load base system prompt (supports hot-reload when role is specified)
        String basePrompt = resolveBaseSystemPrompt();
        if (basePrompt != null && !basePrompt.isBlank()) {
            request = request.system(basePrompt);
        }

        // Add any additional system prompt (e.g., persona, memory context)
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request = request.system(systemPrompt);
        }

        // Apply temperature override if provided
        if (temperature != null) {
            log.debug("Applying temperature override: {}", temperature);
            request = request.options(ChatOptionsBuilder.builder().withTemperature(temperature).build());
        }

        // CRITICAL: Wrap the blocking AI call with timeout protection
        // This prevents test runs from hanging forever if AI provider is slow/hung
        final ChatClient.ChatClientRequestSpec finalRequest = request;
        final long apiCallStart = System.currentTimeMillis();

        log.debug("[AI API] Sending request to provider... (timeout: {}s)", AI_CALL_TIMEOUT_SECONDS);

        // Use get() with timeout instead of orTimeout().join() to enable proper cancellation
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
                () -> finalRequest.user(userPrompt).call().chatResponse(),
                TIMEOUT_EXECUTOR
        );

        try {
            ChatResponse response = future.get(AI_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long apiCallDuration = System.currentTimeMillis() - apiCallStart;
            log.debug("[AI API] Response received in {}ms", apiCallDuration);

            return response;
        } catch (TimeoutException e) {
            long apiCallDuration = System.currentTimeMillis() - apiCallStart;

            // Cancel the underlying task to prevent orphaned HTTP requests
            boolean cancelled = future.cancel(true);
            log.warn("[AI TIMEOUT] Cancelled underlying task: {}", cancelled);

            String errorMsg = String.format(
                    "AI API call timed out after %d seconds (actual: %dms). Provider may be slow or unresponsive. " +
                    "Prompt size: %d chars. Consider checking Anthropic dashboard for rate limits or service issues.",
                    AI_CALL_TIMEOUT_SECONDS, apiCallDuration, userPrompt.length());
            log.error("[AI TIMEOUT] {}", errorMsg);
            throw new RuntimeException(errorMsg, e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI API call interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            long apiCallDuration = System.currentTimeMillis() - apiCallStart;
            Throwable cause = e.getCause();
            log.error("[AI API ERROR] Failed after {}ms: {}", apiCallDuration, extractErrorMessage(e));
            throw new RuntimeException("AI API call failed: " + extractErrorMessage(e), cause);
        }
    }

    /**
     * Checks if the exception is a rate limit error that should be retried.
     * Handles GCP Vertex AI RESOURCE_EXHAUSTED and other 429 errors.
     */
    private boolean isRateLimitError(Exception e) {
        String message = getFullErrorMessage(e);

        // Check for common rate limit indicators
        return message.contains("RESOURCE_EXHAUSTED")
                || message.contains("429")
                || message.contains("rate limit")
                || message.contains("Rate limit")
                || message.contains("quota")
                || message.contains("too many requests")
                || message.contains("Too Many Requests");
    }

    /**
     * Gets the full error message including nested exceptions.
     */
    private String getFullErrorMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        while (t != null) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" | ");
            }
            sb.append(t.getClass().getSimpleName()).append(" | ");
            t = t.getCause();
        }
        return sb.toString();
    }

    /**
     * Extracts a user-friendly error message.
     */
    private String extractErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown error";
        }

        // Find the root cause message
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : e.getMessage();
    }

    /**
     * Calculates random exponential backoff delay per GCP guidance.
     *
     * <p>Uses truncated exponential backoff with jitter:
     * Randomly wait up to (2^attempt * multiplier) seconds, capped at MAX_DELAY.
     * This follows GCP's recommended approach for handling 429 errors.
     *
     * @see <a href="https://cloud.google.com/blog/products/ai-machine-learning/learn-how-to-handle-429-resource-exhaustion-errors-in-your-llms">GCP LLM retry guidance</a>
     */
    private long calculateBackoffDelay(int attempt) {
        // Calculate max delay for this attempt: 2^attempt * multiplier (in seconds)
        // attempt 0: 1s, attempt 1: 2s, attempt 2: 4s, attempt 3: 8s, etc.
        long maxDelayForAttempt = (1L << (attempt + 1)) * BACKOFF_MULTIPLIER * 1000; // Convert to ms
        long cappedDelay = Math.min(maxDelayForAttempt, MAX_DELAY_MS);

        // Random exponential: pick a random value within [cappedDelay/2, cappedDelay]
        // This provides jitter while ensuring we don't wait too short
        long minDelay = cappedDelay / 2;
        return minDelay + (long) (RANDOM.nextDouble() * (cappedDelay - minDelay));
    }

    /**
     * Attempts to extract a Retry-After delay from the error message.
     *
     * <p>GCP Vertex AI sometimes includes specific retry delays in error messages like:
     * "Please retry in 34.074824224s"
     *
     * @return Optional containing delay in milliseconds, or empty if not found
     */
    private java.util.Optional<Long> extractRetryAfterMs(Exception e) {
        String message = getFullErrorMessage(e);

        // Look for patterns like "retry in 34.074824224s" or "retry after 30s"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "retry\\s+(?:in|after)\\s+([\\d.]+)\\s*s",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            try {
                double seconds = Double.parseDouble(matcher.group(1));
                long delayMs = (long) (seconds * 1000);
                // Cap at MAX_DELAY and ensure minimum of 1 second
                delayMs = Math.max(1000, Math.min(delayMs, MAX_DELAY_MS));
                log.debug("Extracted Retry-After delay: {}ms from error message", delayMs);
                return java.util.Optional.of(delayMs);
            } catch (NumberFormatException nfe) {
                log.debug("Failed to parse retry delay from: {}", matcher.group(1));
            }
        }

        return java.util.Optional.empty();
    }

    private int safeToInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    /**
     * Resolves the base system prompt at call time.
     *
     * <p>When role is specified and promptLoader is available, loads the prompt
     * from external files (supports hot-reload). Otherwise falls back to
     * defaultSystemPrompt.
     *
     * @return The resolved system prompt, or null if none configured
     */
    private String resolveBaseSystemPrompt() {
        // If role and prompt loader are configured, use hot-reload
        if (role != null && promptLoader != null) {
            return loadPromptForRole();
        }

        // Fall back to legacy defaultSystemPrompt
        return defaultSystemPrompt;
    }

    /**
     * Loads the appropriate prompt based on role and mode.
     */
    private String loadPromptForRole() {
        boolean useAriaMode = browserConfig != null && browserConfig.isAriaEffectivelyEnabled();

        return switch (role) {
            case "architect" -> promptLoader.getArchitectPrompt();
            case "hunter" -> promptLoader.getHunterPrompt(useAriaMode);
            case "healer" -> promptLoader.getHealerPrompt(useAriaMode);
            case "reporter" -> promptLoader.getPrompt("reporter", "system");
            default -> defaultSystemPrompt;
        };
    }

    /**
     * Executes a chat call with custom timeout.
     *
     * <p>Use this for long-running AI calls like report generation that may need
     * more time than the default timeout.
     *
     * @param systemPrompt   System prompt to steer behavior (optional)
     * @param userPrompt     User prompt content
     * @param timeoutSeconds Custom timeout in seconds (null uses default 120s)
     * @return Model response content
     */
    public String callWithTimeout(String systemPrompt, String userPrompt, Long timeoutSeconds) {
        long effectiveTimeout = timeoutSeconds != null ? timeoutSeconds : AI_CALL_TIMEOUT_SECONDS;

        log.info("[AI CALL START] Role: {}, Model: {}, Prompt: {} chars, Custom timeout: {}s",
                role, modelName, userPrompt.length(), effectiveTimeout);

        long startTime = System.currentTimeMillis();

        try {
            ChatResponse response = executeRequestWithTimeout(chatClient, systemPrompt, userPrompt, null, effectiveTimeout);
            long latencyMs = System.currentTimeMillis() - startTime;

            String content = response.getResult() != null
                    ? response.getResult().getOutput().getContent()
                    : "";

            log.info("[AI CALL SUCCESS] Role: {}, Model: {}, Latency: {}ms", role, modelName, latencyMs);
            return content;

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("[AI CALL FAILED] Role: {}, Model: {}, Latency: {}ms, Error: {}",
                    role, modelName, latencyMs, extractErrorMessage(e));
            throw new RuntimeException("AI call failed: " + extractErrorMessage(e), e);
        }
    }

    /**
     * Executes request with custom timeout and proper task cancellation.
     *
     * <p>Unlike orTimeout() which only completes the future exceptionally without
     * cancelling the underlying task, this implementation properly cancels the
     * task on timeout to prevent orphaned HTTP requests.
     */
    private ChatResponse executeRequestWithTimeout(ChatClient client, String systemPrompt,
                                                    String userPrompt, Double temperature, long timeoutSeconds) {
        // Build the request
        ChatClient.ChatClientRequestSpec request = client.prompt();

        String basePrompt = resolveBaseSystemPrompt();
        if (basePrompt != null && !basePrompt.isBlank()) {
            request = request.system(basePrompt);
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            request = request.system(systemPrompt);
        }

        if (temperature != null) {
            request = request.options(ChatOptionsBuilder.builder().withTemperature(temperature).build());
        }

        final ChatClient.ChatClientRequestSpec finalRequest = request;
        final long apiCallStart = System.currentTimeMillis();

        log.debug("[AI API] Sending request with custom timeout: {}s", timeoutSeconds);

        // Use get() with timeout instead of orTimeout().join() to enable proper cancellation
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(
                () -> finalRequest.user(userPrompt).call().chatResponse(),
                TIMEOUT_EXECUTOR
        );

        try {
            ChatResponse response = future.get(timeoutSeconds, TimeUnit.SECONDS);

            long apiCallDuration = System.currentTimeMillis() - apiCallStart;
            log.debug("[AI API] Response received in {}ms (timeout was {}s)", apiCallDuration, timeoutSeconds);

            return response;
        } catch (TimeoutException e) {
            long apiCallDuration = System.currentTimeMillis() - apiCallStart;

            // Cancel the underlying task to prevent orphaned HTTP requests
            boolean cancelled = future.cancel(true);
            log.warn("[AI TIMEOUT] Cancelled underlying task: {}", cancelled);

            String errorMsg = String.format(
                    "AI API call timed out after %d seconds (actual: %dms). Prompt size: %d chars.",
                    timeoutSeconds, apiCallDuration, userPrompt.length());
            log.error("[AI TIMEOUT] {}", errorMsg);
            throw new RuntimeException(errorMsg, e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("AI API call interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            long apiCallDuration = System.currentTimeMillis() - apiCallStart;
            Throwable cause = e.getCause();
            log.error("[AI API ERROR] Failed after {}ms: {}", apiCallDuration, extractErrorMessage(e));
            throw new RuntimeException("AI API call failed: " + extractErrorMessage(e), cause);
        }
    }
}
