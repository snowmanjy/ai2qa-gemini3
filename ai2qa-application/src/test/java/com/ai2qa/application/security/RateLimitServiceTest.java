package com.ai2qa.application.security;

import com.ai2qa.application.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RateLimitService Tests")
class RateLimitServiceTest {

    // ========== User Rate Limit Tests ==========

    @Nested
    @DisplayName("User Rate Limiting")
    class UserRateLimitTests {

        @Test
        @DisplayName("Should allow requests within user limit")
        void shouldAllowRequestsWithinLimit() {
            RateLimitService service = new RateLimitService(true, 5, 100, 100);

            // Should allow 5 requests
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() -> service.checkRateLimits("user1", "192.168.1.1", "example.com"));
            }
        }

        @Test
        @DisplayName("Should block requests exceeding user limit")
        void shouldBlockRequestsExceedingLimit() {
            RateLimitService service = new RateLimitService(true, 3, 100, 100);

            // Use up the limit
            for (int i = 0; i < 3; i++) {
                service.checkRateLimits("user1", "192.168.1.1", "example.com");
            }

            // Next request should be blocked
            RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                    () -> service.checkRateLimits("user1", "192.168.1.1", "example.com"));
            assertTrue(ex.getMessage().contains("User rate limit"));
        }

        @Test
        @DisplayName("Should track different users separately")
        void shouldTrackDifferentUsersSeparately() {
            RateLimitService service = new RateLimitService(true, 2, 100, 100);

            // User1 uses their limit
            service.checkRateLimits("user1", "192.168.1.1", "example.com");
            service.checkRateLimits("user1", "192.168.1.1", "example.com");

            // User2 should still have their full limit
            assertDoesNotThrow(() -> service.checkRateLimits("user2", "192.168.1.1", "example.com"));
            assertDoesNotThrow(() -> service.checkRateLimits("user2", "192.168.1.1", "example.com"));
        }
    }

    // ========== IP Rate Limit Tests ==========

    @Nested
    @DisplayName("IP Rate Limiting")
    class IpRateLimitTests {

        @Test
        @DisplayName("Should allow requests within IP limit")
        void shouldAllowRequestsWithinLimit() {
            RateLimitService service = new RateLimitService(true, 100, 5, 100);

            // Should allow 5 requests from same IP
            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(() -> service.checkRateLimits(null, "192.168.1.1", "example.com"));
            }
        }

        @Test
        @DisplayName("Should block requests exceeding IP limit")
        void shouldBlockRequestsExceedingLimit() {
            RateLimitService service = new RateLimitService(true, 100, 3, 100);

            // Use up the limit
            for (int i = 0; i < 3; i++) {
                service.checkRateLimits(null, "192.168.1.1", "example.com");
            }

            // Next request should be blocked
            RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                    () -> service.checkRateLimits(null, "192.168.1.1", "example.com"));
            assertTrue(ex.getMessage().contains("IP rate limit"));
        }

        @Test
        @DisplayName("Should track different IPs separately")
        void shouldTrackDifferentIpsSeparately() {
            RateLimitService service = new RateLimitService(true, 100, 2, 100);

            // IP1 uses their limit
            service.checkRateLimits(null, "192.168.1.1", "example.com");
            service.checkRateLimits(null, "192.168.1.1", "example.com");

            // IP2 should still have their full limit
            assertDoesNotThrow(() -> service.checkRateLimits(null, "192.168.1.2", "example.com"));
        }
    }

    // ========== Target Rate Limit Tests ==========

    @Nested
    @DisplayName("Target Domain Rate Limiting")
    class TargetRateLimitTests {

        @Test
        @DisplayName("Should allow requests within target limit")
        void shouldAllowRequestsWithinLimit() {
            RateLimitService service = new RateLimitService(true, 100, 100, 5);

            // Should allow 5 requests to same target (different users/IPs)
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                assertDoesNotThrow(() -> service.checkRateLimits("user" + idx, "192.168.1." + idx, "example.com"));
            }
        }

        @Test
        @DisplayName("Should block requests exceeding target limit")
        void shouldBlockRequestsExceedingLimit() {
            RateLimitService service = new RateLimitService(true, 100, 100, 3);

            // Use up the limit (different users/IPs, same target)
            for (int i = 0; i < 3; i++) {
                service.checkRateLimits("user" + i, "192.168.1." + i, "example.com");
            }

            // Next request should be blocked
            RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                    () -> service.checkRateLimits("user99", "192.168.1.99", "example.com"));
            assertTrue(ex.getMessage().contains("Target rate limit"));
        }

        @Test
        @DisplayName("Should track different targets separately")
        void shouldTrackDifferentTargetsSeparately() {
            RateLimitService service = new RateLimitService(true, 100, 100, 2);

            // example.com uses their limit
            service.checkRateLimits("user1", "192.168.1.1", "example.com");
            service.checkRateLimits("user2", "192.168.1.2", "example.com");

            // different-site.com should still have their full limit
            assertDoesNotThrow(() -> service.checkRateLimits("user3", "192.168.1.3", "different-site.com"));
        }

        @Test
        @DisplayName("Should normalize target domain to lowercase")
        void shouldNormalizeTargetDomain() {
            RateLimitService service = new RateLimitService(true, 100, 100, 2);

            // Mixed case should be treated as same domain
            service.checkRateLimits("user1", "192.168.1.1", "Example.COM");
            service.checkRateLimits("user2", "192.168.1.2", "example.com");

            // Should be blocked - counts as same target
            assertThrows(RateLimitExceededException.class,
                    () -> service.checkRateLimits("user3", "192.168.1.3", "EXAMPLE.COM"));
        }
    }

    // ========== Disabled Rate Limiting Tests ==========

    @Nested
    @DisplayName("Disabled Rate Limiting")
    class DisabledRateLimitTests {

        @Test
        @DisplayName("Should allow all requests when disabled")
        void shouldAllowAllRequestsWhenDisabled() {
            RateLimitService service = new RateLimitService(false, 1, 1, 1);

            // Should allow unlimited requests when disabled
            for (int i = 0; i < 100; i++) {
                assertDoesNotThrow(() -> service.checkRateLimits("user1", "192.168.1.1", "example.com"));
            }
        }
    }

    // ========== Null/Empty Input Tests ==========

    @Nested
    @DisplayName("Null/Empty Input Handling")
    class NullInputTests {

        @Test
        @DisplayName("Should handle null user ID")
        void shouldHandleNullUserId() {
            RateLimitService service = new RateLimitService(true, 1, 100, 100);

            // Should not throw with null user ID
            assertDoesNotThrow(() -> service.checkRateLimits(null, "192.168.1.1", "example.com"));
        }

        @Test
        @DisplayName("Should handle empty user ID")
        void shouldHandleEmptyUserId() {
            RateLimitService service = new RateLimitService(true, 1, 100, 100);

            // Should not throw with empty user ID
            assertDoesNotThrow(() -> service.checkRateLimits("", "192.168.1.1", "example.com"));
        }

        @Test
        @DisplayName("Should handle null client IP")
        void shouldHandleNullClientIp() {
            RateLimitService service = new RateLimitService(true, 100, 1, 100);

            // Should not throw with null IP
            assertDoesNotThrow(() -> service.checkRateLimits("user1", null, "example.com"));
        }

        @Test
        @DisplayName("Should handle null target domain")
        void shouldHandleNullTargetDomain() {
            RateLimitService service = new RateLimitService(true, 100, 100, 1);

            // Should not throw with null target
            assertDoesNotThrow(() -> service.checkRateLimits("user1", "192.168.1.1", null));
        }
    }

    // ========== Stats Tests ==========

    @Nested
    @DisplayName("Statistics")
    class StatsTests {

        @Test
        @DisplayName("Should track bucket counts")
        void shouldTrackBucketCounts() {
            RateLimitService service = new RateLimitService(true, 100, 100, 100);

            // Initial stats should be zero
            RateLimitService.RateLimitStats initialStats = service.getStats();
            assertEquals(0, initialStats.activeUserBuckets());
            assertEquals(0, initialStats.activeIpBuckets());
            assertEquals(0, initialStats.activeTargetBuckets());

            // After some requests
            service.checkRateLimits("user1", "192.168.1.1", "example.com");
            service.checkRateLimits("user2", "192.168.1.2", "other.com");

            RateLimitService.RateLimitStats stats = service.getStats();
            assertEquals(2, stats.activeUserBuckets());
            assertEquals(2, stats.activeIpBuckets());
            assertEquals(2, stats.activeTargetBuckets());
        }
    }
}
