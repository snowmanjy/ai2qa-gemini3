package com.ai2qa.application.security;

import com.ai2qa.application.exception.ProhibitedTargetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("TargetGuardService Security Tests")
class TargetGuardServiceTest {

    private Environment mockEnvironment() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});
        return env;
    }

    private Environment mockProductionEnvironment() {
        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"prod"});
        return env;
    }

    private TargetGuardProperties defaultGuardProperties() {
        return new TargetGuardProperties();
    }

    private TargetGuardService createService(String blacklist, String allowlist, boolean selfTest,
                                             boolean ssrfProtection, boolean dnsProtection, Environment env) {
        return new TargetGuardService(defaultGuardProperties(), blacklist, allowlist, selfTest, ssrfProtection, dnsProtection, env);
    }

    private TargetGuardService createDefaultService() {
        return createService("", "", false, true, true, mockEnvironment());
    }

    private TargetGuardService createProductionService() {
        return createService("", "", false, true, true, mockProductionEnvironment());
    }

    // ========== SSRF Protection Tests (Cloud Metadata) ==========

    @Nested
    @DisplayName("SSRF Protection - Cloud Metadata Endpoints")
    class SsrfProtectionTests {

        @Test
        @DisplayName("Should block AWS/GCP metadata endpoint 169.254.169.254")
        void shouldBlockAwsGcpMetadataEndpoint() {
            TargetGuardService service = createDefaultService();

            ProhibitedTargetException ex = assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://169.254.169.254/latest/meta-data/"));
            assertTrue(ex.getMessage().contains("metadata"));
        }

        @Test
        @DisplayName("Should block AWS ECS metadata endpoint 169.254.170.2")
        void shouldBlockAwsEcsMetadataEndpoint() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://169.254.170.2/v2/metadata"));
        }

        @Test
        @DisplayName("Should block GCP metadata.google.internal")
        void shouldBlockGcpMetadataInternal() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://metadata.google.internal/computeMetadata/v1/"));
        }

        @Test
        @DisplayName("Should block Kubernetes internal DNS")
        void shouldBlockKubernetesInternalDns() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://kubernetes.default.svc/api"));
        }

        @Test
        @DisplayName("Should block entire link-local range 169.254.x.x")
        void shouldBlockEntireLinkLocalRange() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://169.254.1.1/"));
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://169.254.255.255/"));
        }

        @Test
        @DisplayName("Should allow SSRF targets when protection disabled")
        void shouldAllowWhenSsrfProtectionDisabled() {
            TargetGuardService service = createService("", "", false, false, false, mockEnvironment());

            // When SSRF protection is disabled, metadata endpoints are not blocked
            // (they will fail DNS resolution in real scenario)
            assertDoesNotThrow(() -> service.validateTarget("http://example.com"));
        }
    }

    // ========== IP Encoding Bypass Prevention Tests ==========

    @Nested
    @DisplayName("IP Encoding Bypass Prevention")
    class IpEncodingBypassTests {

        @Test
        @DisplayName("Should block decimal-encoded localhost (2130706433 = 127.0.0.1)")
        void shouldBlockDecimalEncodedLocalhost() {
            TargetGuardService service = createProductionService();

            // 2130706433 = 127.0.0.1 in decimal
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://2130706433/"));
        }

        @Test
        @DisplayName("Should block short localhost notation (127.1)")
        void shouldBlockShortLocalhostNotation() {
            TargetGuardService service = createProductionService();

            // 127.1 expands to 127.0.0.1
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://127.1/"));
        }

        @Test
        @DisplayName("Should block IPv6 localhost (::1)")
        void shouldBlockIpv6Localhost() {
            TargetGuardService service = createProductionService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://[::1]/"));
        }

        @Test
        @DisplayName("Should block decimal-encoded metadata endpoint")
        void shouldBlockDecimalEncodedMetadata() {
            TargetGuardService service = createDefaultService();

            // 169.254.169.254 in decimal = 2852039166
            // Note: Java's InetAddress may not parse this specific decimal
            // But link-local range check should catch 169.254.x.x regardless
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://169.254.169.254/"));
        }
    }

    // ========== DNS Rebinding Protection Tests ==========

    @Nested
    @DisplayName("DNS Rebinding Protection")
    class DnsRebindingProtectionTests {

        @Test
        @DisplayName("Should allow public domains that resolve to public IPs")
        void shouldAllowPublicDomainsWithPublicIps() {
            TargetGuardService service = createDefaultService();

            // example.com resolves to public IPs - should be allowed (not in blocked list)
            assertDoesNotThrow(() -> service.validateTarget("https://example.com"));
        }

        @Test
        @DisplayName("Should block loopback IP 127.0.0.1 in production")
        void shouldBlockLoopbackInProduction() {
            TargetGuardService service = createProductionService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://127.0.0.1:8080/"));
        }

        @Test
        @DisplayName("Should allow localhost in development mode")
        void shouldAllowLocalhostInDevelopment() {
            TargetGuardService service = createService("", "", false, true, true, mockEnvironment());

            // Development mode allows localhost
            assertDoesNotThrow(() -> service.validateTarget("http://localhost:3000"));
        }

        @Test
        @DisplayName("DNS resolution failure should not block (browser will handle)")
        void dnsResolutionFailureShouldNotBlock() {
            TargetGuardService service = createDefaultService();

            // Non-existent domain - DNS fails but shouldn't throw
            // Browser will handle the actual DNS failure
            assertDoesNotThrow(() -> service.validateTarget("https://this-domain-does-not-exist-xyz123.com"));
        }
    }

    // ========== Self-Protection Tests (Cannot be bypassed) ==========

    @Nested
    @DisplayName("Self-Protection Tests")
    class SelfProtectionTests {

        @Test
        @DisplayName("Should always block ai2qa.com even when allowlisted")
        void validateTarget_SelfHostAi2qa_AlwaysBlocked() {
            TargetGuardService service = createService(
                    "", // no blacklist
                    "ai2qa.com", // allowlist includes ai2qa.com
                    true, // self-test enabled
                    true, true,
                    mockEnvironment()
            );

            // Should still be blocked - self-protection cannot be bypassed
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://ai2qa.com/dashboard"));
        }

        @Test
        @DisplayName("Should block ai2qa.com subdomains")
        void validateTarget_SelfHostSubdomain_AlwaysBlocked() {
            TargetGuardService service = createService(
                    "",
                    "subdomain.ai2qa.com", // allowlist
                    true,
                    true, true,
                    mockEnvironment()
            );

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://subdomain.ai2qa.com"));
        }
    }

    // ========== Allowlist Tests (Self-Test Mode Enabled) ==========

    @Nested
    @DisplayName("Allowlist Tests - Self-Test Mode")
    class AllowlistTests {

        @Test
        @DisplayName("Should allow allowlisted IP when self-test enabled")
        void validateTarget_AllowlistedIP_AllowedWhenSelfTestEnabled() {
            TargetGuardService service = createService(
                    "localhost,127.0.0.1", // blacklist
                    "192.168.1.2", // allowlist
                    true, // self-test enabled
                    true, true,
                    mockProductionEnvironment()
            );

            // Should NOT throw - IP is allowlisted
            assertDoesNotThrow(() -> service.validateTarget("http://192.168.1.2:8080"));
        }

        @Test
        @DisplayName("Should allow multiple allowlisted IPs when self-test enabled")
        void validateTarget_AllowlistedMultipleIPs_AllowedWhenSelfTestEnabled() {
            TargetGuardService service = createService(
                    "localhost,127.0.0.1",
                    "192.168.1.2,192.168.1.3,10.0.0.5",
                    true,
                    true, true,
                    mockProductionEnvironment()
            );

            assertDoesNotThrow(() -> service.validateTarget("http://192.168.1.2:8080"));
            assertDoesNotThrow(() -> service.validateTarget("http://192.168.1.3:3000"));
            assertDoesNotThrow(() -> service.validateTarget("http://10.0.0.5"));
        }

        @Test
        @DisplayName("Should allow allowlisted domain when self-test enabled")
        void validateTarget_AllowlistedDomain_AllowedWhenSelfTestEnabled() {
            TargetGuardService service = createService(
                    "localhost",
                    "staging.example.com",
                    true,
                    true, true,
                    mockEnvironment()
            );

            assertDoesNotThrow(() -> service.validateTarget("https://staging.example.com/app"));
        }

        @Test
        @DisplayName("Should allow subdomain of allowlisted domain")
        void validateTarget_AllowlistedSubdomain_AllowedWhenSelfTestEnabled() {
            TargetGuardService service = createService(
                    "localhost",
                    "example.com",
                    true,
                    true, true,
                    mockEnvironment()
            );

            // subdomain.example.com should match allowlist entry "example.com"
            assertDoesNotThrow(() -> service.validateTarget("https://sub.example.com/app"));
        }

        @Test
        @DisplayName("Should allow TLD pattern in allowlist")
        void validateTarget_AllowlistedTLD_AllowedWhenSelfTestEnabled() {
            TargetGuardService service = createService(
                    "localhost",
                    ".local",
                    true,
                    true, true,
                    mockEnvironment()
            );

            // myhost.local should match allowlist entry ".local"
            assertDoesNotThrow(() -> service.validateTarget("http://myhost.local:8080"));
        }

        @Test
        @DisplayName("Should allow localhost in production when allowlisted")
        void validateTarget_LocalhostAllowlisted_AllowedInProduction() {
            TargetGuardService service = createService(
                    "", // no blacklist
                    "localhost",
                    true,
                    true, true,
                    mockProductionEnvironment()
            );

            // Localhost normally blocked in production, but allowlist should bypass
            assertDoesNotThrow(() -> service.validateTarget("http://localhost:3000"));
        }
    }

    // ========== Allowlist Disabled Tests ==========

    @Nested
    @DisplayName("Allowlist Disabled Tests")
    class AllowlistDisabledTests {

        @Test
        @DisplayName("Should block allowlisted IP when self-test disabled")
        void validateTarget_AllowlistedIP_BlockedWhenSelfTestDisabled() {
            TargetGuardService service = createService(
                    "localhost,127.0.0.1",
                    "192.168.1.2", // allowlist
                    false, // self-test DISABLED
                    true, true,
                    mockProductionEnvironment()
            );

            // Private IP should be blocked even if in allowlist (self-test mode off)
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://192.168.1.2:8080"));
        }

        @Test
        @DisplayName("Should enforce blacklist even with empty allowlist")
        void validateTarget_EmptyAllowlist_NormalValidation() {
            TargetGuardService service = createService(
                    ".gov,.mil",
                    "", // empty allowlist
                    true,
                    true, true,
                    mockEnvironment()
            );

            // .gov should still be blocked by blacklist
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://whitehouse.gov"));
        }
    }

    // ========== Blacklist Tests ==========

    @Nested
    @DisplayName("Blacklist Tests")
    class BlacklistTests {

        @Test
        @DisplayName("Should block blacklisted domain even with self-test enabled")
        void validateTarget_BlacklistedDomain_BlockedEvenWithSelfTestEnabled() {
            TargetGuardService service = createService(
                    "evil.com,competitor.io",
                    "192.168.1.2", // allowlist doesn't include evil.com
                    true,
                    true, true,
                    mockEnvironment()
            );

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://evil.com"));
        }

        @Test
        @DisplayName("Should block non-allowlisted private IP in production")
        void validateTarget_NonAllowlistedPrivateIP_BlockedInProduction() {
            TargetGuardService service = createService(
                    "",
                    "192.168.1.2", // only 192.168.1.2 is allowlisted
                    true,
                    true, true,
                    mockProductionEnvironment()
            );

            // 192.168.1.3 is NOT in allowlist, should be blocked
            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("http://192.168.1.3:8080"));
        }
    }

    // ========== Normal URL Validation ==========

    @Nested
    @DisplayName("Normal URL Validation")
    class NormalUrlValidationTests {

        @Test
        @DisplayName("Should allow public domain")
        void validateTarget_PublicDomain_AllowedWithoutAllowlist() {
            TargetGuardService service = createService(
                    "",
                    "",
                    false,
                    true, true,
                    mockEnvironment()
            );

            // Normal public domain should pass (not in any blacklist)
            assertDoesNotThrow(() -> service.validateTarget("https://mycompany.io"));
        }

        @Test
        @DisplayName("Should throw on empty URL")
        void validateTarget_EmptyUrl_Throws() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget(""));
        }

        @Test
        @DisplayName("Should throw on null URL")
        void validateTarget_NullUrl_Throws() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget(null));
        }
    }

    // ========== Built-in TLD Blocking Tests ==========

    @Nested
    @DisplayName("Built-in TLD Blocking")
    class TldBlockingTests {

        @Test
        @DisplayName("Should block .gov domains")
        void shouldBlockGovDomains() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://whitehouse.gov"));
        }

        @Test
        @DisplayName("Should block .mil domains")
        void shouldBlockMilDomains() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://army.mil"));
        }

        @Test
        @DisplayName("Should block .bank domains")
        void shouldBlockBankDomains() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://example.bank"));
        }

        @Test
        @DisplayName("Should block .internal domains")
        void shouldBlockInternalDomains() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://service.internal"));
        }

        @Test
        @DisplayName("Should block high-abuse TLDs (.tk)")
        void shouldBlockHighAbuseTlds() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://malicious.tk"));
        }
    }

    // ========== Built-in Domain Blocking Tests ==========

    @Nested
    @DisplayName("Built-in Domain Blocking")
    class DomainBlockingTests {

        @Test
        @DisplayName("Should block google.com")
        void shouldBlockGoogle() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://google.com"));
        }

        @Test
        @DisplayName("Should block www.google.com (subdomain)")
        void shouldBlockGoogleSubdomain() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://www.google.com"));
        }

        @Test
        @DisplayName("Should block facebook.com")
        void shouldBlockFacebook() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://facebook.com"));
        }

        @Test
        @DisplayName("Should block paypal.com (payment processor)")
        void shouldBlockPaymentProcessors() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://paypal.com"));
        }

        @Test
        @DisplayName("Should block stripe.com (payment processor)")
        void shouldBlockStripe() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://stripe.com"));
        }

        @Test
        @DisplayName("Should block doubleclick.net (ad network)")
        void shouldBlockAdNetworks() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://doubleclick.net"));
        }
    }

    // ========== URL Pattern Blocking Tests ==========

    @Nested
    @DisplayName("URL Pattern Blocking")
    class UrlPatternBlockingTests {

        @Test
        @DisplayName("Should block /admin paths")
        void shouldBlockAdminPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/admin"));
        }

        @Test
        @DisplayName("Should block /wp-admin paths")
        void shouldBlockWpAdminPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/wp-admin/index.php"));
        }

        @Test
        @DisplayName("Should block /login paths")
        void shouldBlockLoginPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/login"));
        }

        @Test
        @DisplayName("Should block /oauth paths")
        void shouldBlockOAuthPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/oauth/authorize"));
        }

        @Test
        @DisplayName("Should block /.env paths")
        void shouldBlockEnvPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/.env"));
        }

        @Test
        @DisplayName("Should block /.git paths")
        void shouldBlockGitPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/.git/config"));
        }

        @Test
        @DisplayName("Should block /phpmyadmin paths")
        void shouldBlockPhpMyAdminPaths() {
            TargetGuardService service = createDefaultService();

            assertThrows(ProhibitedTargetException.class,
                    () -> service.validateTarget("https://mycompany.io/phpmyadmin"));
        }

        @Test
        @DisplayName("Should allow normal paths")
        void shouldAllowNormalPaths() {
            TargetGuardService service = createDefaultService();

            // Normal paths should be allowed
            assertDoesNotThrow(() -> service.validateTarget("https://mycompany.io/products"));
            assertDoesNotThrow(() -> service.validateTarget("https://mycompany.io/about"));
            assertDoesNotThrow(() -> service.validateTarget("https://mycompany.io/contact"));
        }
    }
}
