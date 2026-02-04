package com.ai2qa.application.security;

import com.ai2qa.application.security.ThreatIntelligenceService.ThreatResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ThreatIntelligenceService.
 *
 * <p>Note: These tests do not call the actual Google API.
 * Integration tests with the real API should be run manually with valid credentials.
 */
@DisplayName("ThreatIntelligenceService Tests")
class ThreatIntelligenceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("When disabled")
    class WhenDisabled {

        @Test
        @DisplayName("Should return NOT_CHECKED when disabled")
        void shouldReturnNotCheckedWhenDisabled() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    false, "", 9000, objectMapper);

            ThreatResult result = service.checkUrl("https://example.com");

            assertThat(result.status()).isEqualTo(ThreatResult.Status.NOT_CHECKED);
            assertThat(result.message()).contains("disabled");
            assertThat(result.isThreat()).isFalse();
            assertThat(result.isSafe()).isFalse();
        }

        @Test
        @DisplayName("Should report not enabled")
        void shouldReportNotEnabled() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    false, "", 9000, objectMapper);

            assertThat(service.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("When enabled without API key")
    class WhenEnabledWithoutApiKey {

        @Test
        @DisplayName("Should return NOT_CHECKED when API key is missing")
        void shouldReturnNotCheckedWithoutApiKey() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    true, "", 9000, objectMapper);

            ThreatResult result = service.checkUrl("https://example.com");

            assertThat(result.status()).isEqualTo(ThreatResult.Status.NOT_CHECKED);
            assertThat(service.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return NOT_CHECKED when API key is blank")
        void shouldReturnNotCheckedWithBlankApiKey() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    true, "   ", 9000, objectMapper);

            ThreatResult result = service.checkUrl("https://example.com");

            assertThat(result.status()).isEqualTo(ThreatResult.Status.NOT_CHECKED);
            assertThat(service.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("ThreatResult")
    class ThreatResultTests {

        @Test
        @DisplayName("Safe result should have correct status")
        void safeResultShouldHaveCorrectStatus() {
            ThreatResult result = ThreatResult.safe();

            assertThat(result.status()).isEqualTo(ThreatResult.Status.SAFE);
            assertThat(result.isSafe()).isTrue();
            assertThat(result.isThreat()).isFalse();
            assertThat(result.threatType()).isNull();
        }

        @Test
        @DisplayName("Threat result should contain threat details")
        void threatResultShouldContainDetails() {
            ThreatResult result = ThreatResult.threat("MALWARE", "WINDOWS");

            assertThat(result.status()).isEqualTo(ThreatResult.Status.THREAT);
            assertThat(result.isThreat()).isTrue();
            assertThat(result.isSafe()).isFalse();
            assertThat(result.threatType()).isEqualTo("MALWARE");
            assertThat(result.platformType()).isEqualTo("WINDOWS");
            assertThat(result.message()).contains("MALWARE");
        }

        @Test
        @DisplayName("Error result should contain error message")
        void errorResultShouldContainMessage() {
            ThreatResult result = ThreatResult.error("Connection timeout");

            assertThat(result.status()).isEqualTo(ThreatResult.Status.ERROR);
            assertThat(result.isThreat()).isFalse();
            assertThat(result.isSafe()).isFalse();
            assertThat(result.message()).isEqualTo("Connection timeout");
        }
    }

    @Nested
    @DisplayName("Caching")
    class CachingTests {

        @Test
        @DisplayName("Should clear cache when requested")
        void shouldClearCache() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    false, "", 9000, objectMapper);

            // Just verify clearCache doesn't throw
            service.clearCache();
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    class RateLimitingTests {

        @Test
        @DisplayName("Should initialize with configured daily limit")
        void shouldInitializeWithConfiguredDailyLimit() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    false, "", 5000, objectMapper);

            assertThat(service.getDailyRateLimit()).isEqualTo(5000);
            assertThat(service.getDailyRequestCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should track request count when disabled (no increment)")
        void shouldNotIncrementCountWhenDisabled() {
            ThreatIntelligenceService service = new ThreatIntelligenceService(
                    false, "", 100, objectMapper);

            // Call checkUrl multiple times - should not increment since disabled
            service.checkUrl("https://example1.com");
            service.checkUrl("https://example2.com");

            // Count should remain 0 since service is disabled (no API calls made)
            assertThat(service.getDailyRequestCount()).isEqualTo(0);
        }
    }
}
