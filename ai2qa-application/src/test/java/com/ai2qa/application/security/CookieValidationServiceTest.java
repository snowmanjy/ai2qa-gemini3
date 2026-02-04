package com.ai2qa.application.security;

import com.ai2qa.application.exception.ProhibitedTargetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CookieValidationService Security Tests")
class CookieValidationServiceTest {

    private CookieValidationService service;

    @BeforeEach
    void setUp() {
        service = new CookieValidationService();
    }

    // ========== Valid Cookie Tests ==========

    @Nested
    @DisplayName("Valid Cookie Scenarios")
    class ValidCookieTests {

        @Test
        @DisplayName("Should allow cookies with matching domain")
        void shouldAllowCookiesWithMatchingDomain() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "example.com"}]
                """;

            assertDoesNotThrow(() -> service.validateCookies(cookies, "https://example.com"));
        }

        @Test
        @DisplayName("Should allow cookies with subdomain of target")
        void shouldAllowCookiesWithParentDomain() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "example.com"}]
                """;

            // Cookie for example.com should be allowed for sub.example.com target
            assertDoesNotThrow(() -> service.validateCookies(cookies, "https://sub.example.com"));
        }

        @Test
        @DisplayName("Should allow cookies with leading dot in domain")
        void shouldAllowCookiesWithLeadingDot() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": ".example.com"}]
                """;

            assertDoesNotThrow(() -> service.validateCookies(cookies, "https://www.example.com"));
        }

        @Test
        @DisplayName("Should allow cookies without explicit domain")
        void shouldAllowCookiesWithoutDomain() {
            String cookies = """
                [{"name": "session", "value": "abc123"}]
                """;

            assertDoesNotThrow(() -> service.validateCookies(cookies, "https://example.com"));
        }

        @Test
        @DisplayName("Should allow multiple valid cookies")
        void shouldAllowMultipleValidCookies() {
            String cookies = """
                [
                    {"name": "session", "value": "abc123", "domain": "example.com"},
                    {"name": "user", "value": "user1", "domain": ".example.com"},
                    {"name": "pref", "value": "dark"}
                ]
                """;

            assertDoesNotThrow(() -> service.validateCookies(cookies, "https://example.com"));
        }

        @Test
        @DisplayName("Should allow empty cookies string")
        void shouldAllowEmptyCookies() {
            assertDoesNotThrow(() -> service.validateCookies("", "https://example.com"));
            assertDoesNotThrow(() -> service.validateCookies(null, "https://example.com"));
            assertDoesNotThrow(() -> service.validateCookies("[]", "https://example.com"));
        }
    }

    // ========== Domain Mismatch Tests ==========

    @Nested
    @DisplayName("Domain Mismatch Tests")
    class DomainMismatchTests {

        @Test
        @DisplayName("Should block cookies with mismatched domain")
        void shouldBlockCookiesWithMismatchedDomain() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "other-site.com"}]
                """;

            ProhibitedTargetException ex = assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://example.com"));
            assertTrue(ex.getMessage().contains("does not match"));
        }

        @Test
        @DisplayName("Should block cookies for parent domain of cookie")
        void shouldBlockCookiesForParentDomainOfCookie() {
            // Cookie for sub.example.com should NOT be allowed for example.com target
            // (cookie would not be sent anyway, but this is a security check)
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "sub.example.com"}]
                """;

            ProhibitedTargetException ex = assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://example.com"));
            assertTrue(ex.getMessage().contains("does not match"));
        }

        @Test
        @DisplayName("Should block cookies for unrelated subdomain")
        void shouldBlockCookiesForUnrelatedSubdomain() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "evil.com"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://notevil.com"));
        }
    }

    // ========== Internal Domain Tests ==========

    @Nested
    @DisplayName("Internal/Metadata Domain Tests")
    class InternalDomainTests {

        @Test
        @DisplayName("Should block cookies for AWS metadata endpoint")
        void shouldBlockCookiesForAwsMetadata() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "169.254.169.254"}]
                """;

            ProhibitedTargetException ex = assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://169.254.169.254"));
            assertTrue(ex.getMessage().contains("blocked for security"));
        }

        @Test
        @DisplayName("Should block cookies for GCP metadata internal")
        void shouldBlockCookiesForGcpMetadata() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "metadata.google.internal"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://metadata.google.internal"));
        }

        @Test
        @DisplayName("Should block cookies for localhost")
        void shouldBlockCookiesForLocalhost() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "localhost"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "http://localhost:3000"));
        }

        @Test
        @DisplayName("Should block cookies for 127.0.0.1")
        void shouldBlockCookiesForLoopback() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "127.0.0.1"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "http://127.0.0.1:8080"));
        }

        @Test
        @DisplayName("Should block cookies for Kubernetes internal")
        void shouldBlockCookiesForKubernetes() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "kubernetes.default.svc"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://kubernetes.default.svc"));
        }

        @Test
        @DisplayName("Should block cookies for .internal TLD")
        void shouldBlockCookiesForInternalTld() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "service.internal"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://service.internal"));
        }

        @Test
        @DisplayName("Should block cookies for .local TLD")
        void shouldBlockCookiesForLocalTld() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "myservice.local"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "http://myservice.local"));
        }

        @Test
        @DisplayName("Should block cookies for link-local IP range")
        void shouldBlockCookiesForLinkLocal() {
            String cookies = """
                [{"name": "token", "value": "secret", "domain": "169.254.1.1"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "http://169.254.1.1"));
        }

        @Test
        @DisplayName("Should block cookies for any IP address")
        void shouldBlockCookiesForIpAddress() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "192.168.1.100"}]
                """;

            // Cookies should not be set for IP addresses (security best practice)
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "http://192.168.1.100"));
        }
    }

    // ========== Invalid Input Tests ==========

    @Nested
    @DisplayName("Invalid Input Tests")
    class InvalidInputTests {

        @Test
        @DisplayName("Should throw on invalid JSON")
        void shouldThrowOnInvalidJson() {
            String cookies = "not valid json";

            ProhibitedTargetException ex = assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "https://example.com"));
            assertTrue(ex.getMessage().contains("Invalid cookies JSON"));
        }

        @Test
        @DisplayName("Should throw on invalid target URL")
        void shouldThrowOnInvalidTargetUrl() {
            String cookies = """
                [{"name": "session", "value": "abc123", "domain": "example.com"}]
                """;

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateCookies(cookies, "not-a-url-://invalid"));
        }
    }
}
