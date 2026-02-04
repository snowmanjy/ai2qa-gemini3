package com.ai2qa.config.analytics;

import com.ai2qa.application.port.AnalyticsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Analytics configuration for hackathon demo.
 *
 * <p>PostHog removed for hackathon - provides no-op implementation.
 */
@Configuration
class PostHogConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PostHogConfiguration.class);

    @Bean
    AnalyticsPort analyticsPort() {
        log.info("Analytics disabled for hackathon demo");
        return new NoOpAnalyticsPort();
    }

    /**
     * No-op implementation when analytics is disabled.
     */
    private static class NoOpAnalyticsPort implements AnalyticsPort {

        @Override
        public void capture(String distinctId, String event, Map<String, Object> properties) {
            // No-op for hackathon
        }

        @Override
        public void identify(String distinctId, Map<String, Object> properties) {
            // No-op for hackathon
        }

        @Override
        public boolean isEnabled() {
            return false;
        }
    }
}
