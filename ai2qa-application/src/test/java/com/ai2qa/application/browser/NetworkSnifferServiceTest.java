package com.ai2qa.application.browser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NetworkSnifferService.
 */
@DisplayName("NetworkSnifferService")
class NetworkSnifferServiceTest {

    private NetworkSnifferService snifferService;

    @BeforeEach
    void setUp() {
        snifferService = new NetworkSnifferService();
    }

    @Nested
    @DisplayName("ErrorCollector")
    class ErrorCollectorTests {

        @Test
        @DisplayName("should capture HTTP 4xx errors")
        void capturesClientErrors() {
            // Given
            var collector = snifferService.startCapturing();

            // When
            collector.recordError(404, "GET", "https://example.com/api/user");
            collector.recordError(401, "POST", "https://example.com/api/login");
            collector.recordError(403, "DELETE", "https://example.com/api/admin");

            // Then
            List<String> errors = collector.getErrors();
            assertThat(errors).hasSize(3);
            assertThat(errors.get(0)).contains("404").contains("Not Found").contains("/api/user");
            assertThat(errors.get(1)).contains("401").contains("Unauthorized").contains("/api/login");
            assertThat(errors.get(2)).contains("403").contains("Forbidden").contains("/api/admin");
        }

        @Test
        @DisplayName("should capture HTTP 5xx errors")
        void capturesServerErrors() {
            // Given
            var collector = snifferService.startCapturing();

            // When
            collector.recordError(500, "POST", "https://api.example.com/submit");
            collector.recordError(502, "GET", "https://api.example.com/data");
            collector.recordError(503, "PUT", "https://api.example.com/update");
            collector.recordError(504, "GET", "https://api.example.com/slow");

            // Then
            List<String> errors = collector.getErrors();
            assertThat(errors).hasSize(4);
            assertThat(errors.get(0)).contains("500").contains("Internal Server Error");
            assertThat(errors.get(1)).contains("502").contains("Bad Gateway");
            assertThat(errors.get(2)).contains("503").contains("Service Unavailable");
            assertThat(errors.get(3)).contains("504").contains("Gateway Timeout");
        }

        @Test
        @DisplayName("should ignore successful responses")
        void ignoresSuccessfulResponses() {
            // Given
            var collector = snifferService.startCapturing();

            // When
            collector.recordError(200, "GET", "https://example.com/ok");
            collector.recordError(201, "POST", "https://example.com/created");
            collector.recordError(204, "DELETE", "https://example.com/deleted");
            collector.recordError(301, "GET", "https://example.com/redirect");

            // Then
            assertThat(collector.getErrors()).isEmpty();
            assertThat(collector.hasErrors()).isFalse();
        }

        @Test
        @DisplayName("should stop capturing after stopAndGetErrors()")
        void stopsCaptureCorrectly() {
            // Given
            var collector = snifferService.startCapturing();
            collector.recordError(500, "POST", "https://example.com/before");

            // When
            List<String> errors = collector.stopAndGetErrors();
            collector.recordError(404, "GET", "https://example.com/after-stop");

            // Then
            assertThat(errors).hasSize(1);
            assertThat(collector.getErrors()).hasSize(1); // Still 1, new error not captured
        }

        @Test
        @DisplayName("should be thread-safe")
        void isThreadSafe() throws InterruptedException {
            // Given
            var collector = snifferService.startCapturing();
            int threadCount = 10;
            int errorsPerThread = 100;

            // When - Multiple threads recording errors concurrently
            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < errorsPerThread; j++) {
                        collector.recordError(500, "GET", "https://example.com/thread-" + threadId + "-" + j);
                    }
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then
            assertThat(collector.errorCount()).isEqualTo(threadCount * errorsPerThread);
        }

        @Test
        @DisplayName("should truncate long URLs")
        void truncatesLongUrls() {
            // Given
            var collector = snifferService.startCapturing();
            String longUrl = "https://example.com/" + "a".repeat(200);

            // When
            collector.recordError(500, "GET", longUrl);

            // Then
            List<String> errors = collector.getErrors();
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).length()).isLessThan(longUrl.length() + 50);
            assertThat(errors.get(0)).contains("...");
        }
    }

    @Nested
    @DisplayName("formatError")
    class FormatErrorTests {

        @Test
        @DisplayName("should format error with status, method, and URL")
        void formatsCorrectly() {
            String formatted = NetworkSnifferService.formatError(500, "POST", "https://api.example.com/login");
            
            assertThat(formatted)
                    .contains("[500")
                    .contains("Internal Server Error")
                    .contains("POST")
                    .contains("https://api.example.com/login");
        }

        @Test
        @DisplayName("should categorize rate limit errors")
        void categorizes429() {
            String formatted = NetworkSnifferService.formatError(429, "GET", "https://api.example.com/data");
            assertThat(formatted).contains("Rate Limited");
        }
    }

    @Nested
    @DisplayName("analyzeForHealer")
    class AnalyzeForHealerTests {

        @Test
        @DisplayName("should return empty string for no errors")
        void emptyForNoErrors() {
            String analysis = snifferService.analyzeForHealer(List.of());
            assertThat(analysis).isEmpty();
        }

        @Test
        @DisplayName("should format errors for Healer consumption")
        void formatsForHealer() {
            // Given
            List<String> errors = List.of(
                    "[500 Internal Server Error] POST https://api.example.com/submit",
                    "[401 Unauthorized] GET https://api.example.com/user"
            );

            // When
            String analysis = snifferService.analyzeForHealer(errors);

            // Then
            assertThat(analysis)
                    .contains("[NETWORK ERRORS DETECTED]")
                    .contains("500")
                    .contains("401")
                    .contains("server error")
                    .contains("auth error");
        }

        @Test
        @DisplayName("should categorize and summarize errors")
        void categorizesSummary() {
            // Given
            List<String> errors = List.of(
                    "[500 Internal Server Error] POST /api/submit",
                    "[500 Internal Server Error] GET /api/data",
                    "[403 Forbidden] GET /api/admin"
            );

            // When
            String analysis = snifferService.analyzeForHealer(errors);

            // Then
            assertThat(analysis).contains("2 server error(s)");
            assertThat(analysis).contains("BLAME THE BACKEND");
            assertThat(analysis).contains("1 auth error(s)");
        }
    }
}
