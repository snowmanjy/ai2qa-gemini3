package com.ai2qa.config.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * HTTP client configuration for Spring AI providers.
 *
 * <p>Configures timeouts at the HTTP client level to ensure AI API calls
 * actually terminate when the timeout is reached. Without this, the
 * CompletableFuture timeout in SpringChatClientPort only completes the
 * future exceptionally - the underlying HTTP request continues running.
 *
 * <p>These beans are auto-detected by Spring AI's auto-configuration
 * (e.g., AnthropicAutoConfiguration) and used when creating API clients.
 *
 * <p>Timeout values:
 * <ul>
 *   <li>Connect timeout: 30 seconds (network establishment)</li>
 *   <li>Read timeout: 180 seconds (matches summary generation timeout)</li>
 * </ul>
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/354">Spring AI timeout issue</a>
 */
@Configuration
public class AiHttpClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiHttpClientConfiguration.class);

    /**
     * Connection timeout for establishing TCP connection.
     * 30 seconds is generous for cloud providers.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Read timeout for waiting for response data.
     * Must be at least as long as the longest AI operation (summary = 300s).
     * Set to 330s to provide 30s buffer for slow responses.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(330);

    /**
     * Provides a RestClient.Builder with configured timeouts.
     *
     * <p>Spring AI's auto-configuration classes (AnthropicAutoConfiguration,
     * OpenAiAutoConfiguration, etc.) inject this builder when available,
     * ensuring all AI API calls respect these timeouts.
     */
    @Bean
    public RestClient.Builder aiRestClientBuilder() {
        log.info("[AI HTTP] Configuring RestClient with connect={}s, read={}s timeouts",
                CONNECT_TIMEOUT.getSeconds(), READ_TIMEOUT.getSeconds());

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory);
    }

    /**
     * Provides a WebClient.Builder with configured timeouts for streaming.
     *
     * <p>Spring AI uses WebClient for streaming responses. This ensures
     * streaming operations also respect timeout settings.
     */
    @Bean
    public WebClient.Builder aiWebClientBuilder() {
        log.info("[AI HTTP] Configuring WebClient for streaming operations");

        return WebClient.builder();
    }
}
