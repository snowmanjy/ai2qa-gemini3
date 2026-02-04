package com.ai2qa.application.security;

import com.ai2qa.application.exception.ProhibitedTargetException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Service to validate target URLs against a security blacklist.
 * Prevents:
 * - Self-testing (infinite loop protection for ai2qa.com)
 * - Government/military sites
 * - Competitor domains
 * - Localhost in production
 * - Cloud metadata endpoint SSRF (169.254.169.254)
 * - DNS rebinding attacks
 * - IP encoding bypass attacks
 *
 * <p>Blacklists are externalized to configuration via {@link TargetGuardProperties}.
 */
@Service
public class TargetGuardService {

    private static final Logger log = LoggerFactory.getLogger(TargetGuardService.class);

    private final TargetGuardProperties guardProperties;
    private final List<String> blacklist;
    private final List<String> allowlist;
    private final boolean selfTestEnabled;
    private final boolean isProduction;
    private final boolean ssrfProtectionEnabled;
    private final boolean dnsRebindingProtectionEnabled;

    public TargetGuardService(
            TargetGuardProperties guardProperties,
            @Value("${ai2qa.security.target.blacklist:}") String blacklistConfig,
            @Value("${ai2qa.security.target.allowlist:}") String allowlistConfig,
            @Value("${ai2qa.security.target.self-test-enabled:false}") boolean selfTestEnabled,
            @Value("${ai2qa.security.ssrf-protection-enabled:true}") boolean ssrfProtectionEnabled,
            @Value("${ai2qa.security.dns-rebinding-protection:true}") boolean dnsRebindingProtectionEnabled,
            Environment environment) {

        this.guardProperties = guardProperties;
        this.blacklist = parseBlacklist(blacklistConfig);
        this.allowlist = parseAllowlist(allowlistConfig);
        this.selfTestEnabled = selfTestEnabled;
        this.ssrfProtectionEnabled = ssrfProtectionEnabled;
        this.dnsRebindingProtectionEnabled = dnsRebindingProtectionEnabled;
        this.isProduction = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        log.info("TargetGuardService initialized: {} blacklist entries, {} allowlist entries, " +
                        "{} self-protection hosts, {} blocked domains, {} blocked TLDs, " +
                        "selfTestEnabled={}, ssrfProtection={}, dnsRebindingProtection={}, production={}",
                blacklist.size(), allowlist.size(),
                guardProperties.getSelfProtectionHosts().size(),
                guardProperties.getBlockedDomains().size(),
                guardProperties.getBlockedTlds().size(),
                selfTestEnabled, ssrfProtectionEnabled, dnsRebindingProtectionEnabled, isProduction);

        if (selfTestEnabled && !allowlist.isEmpty()) {
            log.warn("Self-test mode ENABLED. Allowlist: {}", allowlist);
        }
    }

    /**
     * Validates a target URL with relaxed rules for LOCAL_AGENT mode.
     * Only applies self-protection (ai2qa.com) and basic URL validation.
     * Skips domain blocking since the browser runs on the user's local machine.
     *
     * @param url The URL to validate
     * @throws ProhibitedTargetException if the target is blocked
     */
    public void validateTargetForLocalAgent(String url) {
        if (url == null || url.isBlank()) {
            throw new ProhibitedTargetException("empty", "Target URL cannot be empty");
        }

        String host = extractHost(url);
        String normalizedHost = normalizeHost(host);

        log.debug("Validating target for LOCAL_AGENT: url={}, host={}", url, host);

        // 1. Self-protection (always blocked - CANNOT be bypassed)
        if (isSelfHost(normalizedHost)) {
            log.warn("Self-testing attempt blocked: {}", url);
            throw new ProhibitedTargetException(normalizedHost,
                    "Self-testing is prohibited to prevent infinite loops");
        }

        // Skip all other checks for LOCAL_AGENT mode:
        // - SSRF protection not needed (browser runs on user's machine)
        // - Domain blocking not needed (user's own browser/credentials)
        // - TLD blocking not needed (user's choice)
        // - URL pattern blocking not needed (user's choice)

        log.debug("Target validated successfully for LOCAL_AGENT: {}", url);
    }

    /**
     * Validates a target URL against security policies.
     *
     * @param url The URL to validate
     * @throws ProhibitedTargetException if the target is blocked
     */
    public void validateTarget(String url) {
        if (url == null || url.isBlank()) {
            throw new ProhibitedTargetException("empty", "Target URL cannot be empty");
        }

        String host = extractHost(url);
        String normalizedHost = normalizeHost(host);

        log.debug("Validating target: url={}, host={}, normalized={}", url, host, normalizedHost);

        // 1. Self-protection (always blocked - CANNOT be bypassed)
        if (isSelfHost(normalizedHost)) {
            log.warn("Self-testing attempt blocked: {}", url);
            throw new ProhibitedTargetException(normalizedHost,
                    "Self-testing is prohibited to prevent infinite loops");
        }

        // 2. SSRF Protection - Block cloud metadata endpoints (CRITICAL)
        // This ALWAYS applies, even for allowlisted hosts (metadata endpoints are never allowed)
        if (ssrfProtectionEnabled && isCloudMetadataEndpoint(host)) {
            log.warn("SSRF attempt blocked - cloud metadata endpoint: {}", url);
            throw new ProhibitedTargetException(host,
                    "Access to cloud metadata endpoints is prohibited");
        }

        // 3. Allowlist check (bypasses DNS rebinding, localhost, and blacklist checks when self-test mode enabled)
        // Must be checked BEFORE DNS rebinding protection to allow internal IPs for testing
        if (selfTestEnabled && isAllowlisted(normalizedHost)) {
            log.info("Self-test mode: allowing target {}", normalizedHost);
            return; // Skip all remaining checks
        }

        // 4. Localhost check (allowed in dev mode, blocked in production)
        // Must be checked BEFORE DNS rebinding protection to allow localhost testing in dev
        if (isLocalhost(normalizedHost)) {
            if (isProduction) {
                log.warn("Localhost testing blocked in production: {}", url);
                throw new ProhibitedTargetException(normalizedHost,
                        "Localhost testing is not allowed in production");
            } else {
                log.debug("Localhost allowed in development mode: {}", url);
                return; // Allow in dev
            }
        }

        // 5. DNS Rebinding Protection - Resolve DNS and validate resolved IPs
        // Only applies to non-allowlisted, non-localhost targets
        if (dnsRebindingProtectionEnabled) {
            validateResolvedIps(host, url);
        }

        // 6. Blacklist check (user-configured)
        findBlacklistMatch(normalizedHost).ifPresent(matchedEntry -> {
            log.warn("Blacklisted domain blocked: {} (matched: {})", url, matchedEntry);
            throw new ProhibitedTargetException(normalizedHost,
                    "This domain is on the prohibited list");
        });

        // 7. Built-in TLD blocking (government, military, financial, infrastructure)
        Optional<String> blockedTld = findBlockedTld(normalizedHost);
        if (blockedTld.isPresent()) {
            log.warn("Blocked TLD detected: {} (matched: {})", url, blockedTld.get());
            throw new ProhibitedTargetException(normalizedHost,
                    "Testing domains with " + blockedTld.get() + " TLD is prohibited");
        }

        // 8. Built-in domain blocking (major platforms, payment processors, ad networks)
        Optional<String> blockedDomain = findBlockedDomain(normalizedHost);
        if (blockedDomain.isPresent()) {
            log.warn("Blocked domain detected: {} (matched: {})", url, blockedDomain.get());
            throw new ProhibitedTargetException(normalizedHost,
                    "Testing " + blockedDomain.get() + " is prohibited");
        }

        // 9. URL pattern validation (admin panels, auth endpoints, sensitive paths)
        Optional<String> blockedPattern = findBlockedUrlPattern(url);
        if (blockedPattern.isPresent()) {
            log.warn("Blocked URL pattern detected: {} (matched: {})", url, blockedPattern.get());
            throw new ProhibitedTargetException(url,
                    "Access to " + blockedPattern.get() + " paths is prohibited");
        }

        log.debug("Target validated successfully: {}", url);
    }

    /**
     * Checks if the host is a cloud metadata endpoint (SSRF protection).
     * Blocks: 169.254.169.254, metadata.google.internal, kubernetes.default, etc.
     */
    private boolean isCloudMetadataEndpoint(String host) {
        if (host == null) return false;
        String normalizedHost = host.toLowerCase().trim();

        // Direct match against known metadata hosts
        if (guardProperties.getBlockedMetadataHostsSet().contains(normalizedHost)) {
            return true;
        }

        // Block entire link-local range (169.254.x.x)
        if (normalizedHost.startsWith("169.254.")) {
            return true;
        }

        // Normalize IP encodings and check again (handles decimal, octal, hex variants)
        Optional<String> canonicalIp = normalizeIpAddress(host);
        if (canonicalIp.isPresent()) {
            String ip = canonicalIp.get();
            if (ip.startsWith("169.254.")) {
                log.warn("Encoded metadata IP detected: {} -> {}", host, ip);
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves DNS for the host and validates that no resolved IP is internal/blocked.
     * Protects against DNS rebinding attacks where initial DNS returns public IP
     * but subsequent resolutions return internal IPs.
     */
    private void validateResolvedIps(String host, String originalUrl) {
        // Skip DNS resolution for known hostnames that are obviously IPs
        Optional<String> canonicalIp = normalizeIpAddress(host);
        if (canonicalIp.isPresent()) {
            String ip = canonicalIp.get();
            if (isBlockedIp(ip)) {
                log.warn("Blocked IP detected (possibly encoded): original={}, canonical={}", host, ip);
                throw new ProhibitedTargetException(host,
                        "IP address resolves to blocked range: " + ip);
            }
            return; // IP was already validated
        }

        // Resolve DNS and check all returned IPs
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                String ip = addr.getHostAddress();
                if (isBlockedIp(ip)) {
                    log.warn("DNS rebinding protection: {} resolves to blocked IP {}", host, ip);
                    throw new ProhibitedTargetException(host,
                            "Domain resolves to blocked IP address: " + ip);
                }
            }
            log.debug("DNS resolution validated for {}: {} IPs checked", host, addresses.length);
        } catch (UnknownHostException e) {
            // DNS resolution failed - this is acceptable, browser will handle it
            log.debug("DNS resolution failed for {} (will be handled by browser): {}", host, e.getMessage());
        }
    }

    /**
     * Normalizes IP address representations (decimal, octal, hex, IPv6-mapped).
     * Returns canonical dotted-decimal format for consistent validation.
     *
     * Examples:
     * - 2130706433 -> 127.0.0.1 (decimal)
     * - 0x7f000001 -> 127.0.0.1 (hex)
     * - 0177.0.0.1 -> 127.0.0.1 (octal)
     * - ::ffff:127.0.0.1 -> 127.0.0.1 (IPv6-mapped)
     */
    private Optional<String> normalizeIpAddress(String host) {
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        try {
            // Strip brackets from IPv6 addresses (e.g., "[::1]" -> "::1")
            String cleanHost = host;
            if (host.startsWith("[") && host.endsWith("]")) {
                cleanHost = host.substring(1, host.length() - 1);
            }
            InetAddress addr = InetAddress.getByName(cleanHost);
            return Optional.of(addr.getHostAddress());
        } catch (UnknownHostException e) {
            // Not a valid IP address or unresolvable hostname
            return Optional.empty();
        }
    }

    /**
     * Checks if an IP address is in a blocked range:
     * - Loopback (127.x.x.x, ::1)
     * - Private networks (10.x, 172.16-31.x, 192.168.x)
     * - Link-local (169.254.x.x) - includes cloud metadata
     */
    private boolean isBlockedIp(String ip) {
        if (ip == null) return false;

        // Loopback - IPv4 and IPv6 (including expanded form 0:0:0:0:0:0:0:1)
        if (ip.startsWith("127.") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }

        // Link-local (cloud metadata)
        if (ip.startsWith("169.254.")) {
            return true;
        }

        // Private networks - only block in production
        if (isProduction) {
            // 10.0.0.0/8
            if (ip.startsWith("10.")) {
                return true;
            }
            // 192.168.0.0/16
            if (ip.startsWith("192.168.")) {
                return true;
            }
            // 172.16.0.0/12 (172.16.x.x - 172.31.x.x)
            if (ip.startsWith("172.")) {
                String[] parts = ip.split("\\.");
                if (parts.length >= 2) {
                    try {
                        int secondOctet = Integer.parseInt(parts[1]);
                        if (secondOctet >= 16 && secondOctet <= 31) {
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        // Invalid IP format
                    }
                }
            }
        }

        return false;
    }

    private String extractHost(String url) {
        try {
            // Add scheme if missing
            String normalizedUrl = url;
            if (!url.contains("://")) {
                normalizedUrl = "https://" + url;
            }
            URI uri = new URI(normalizedUrl);
            String host = uri.getHost();

            // URI.getHost() returns null for non-standard IP formats like 127.1
            // Fall back to authority and strip port if present
            if (host == null) {
                String authority = uri.getAuthority();
                if (authority != null) {
                    // Handle IPv6 addresses in brackets [::1]:8080
                    if (authority.startsWith("[")) {
                        int closeBracket = authority.indexOf(']');
                        if (closeBracket > 0) {
                            return authority.substring(0, closeBracket + 1);
                        }
                    }
                    // Strip port from authority (e.g., "127.1:8080" -> "127.1")
                    int colonIndex = authority.lastIndexOf(':');
                    return colonIndex > 0 ? authority.substring(0, colonIndex) : authority;
                }
            }
            return host != null ? host : url;
        } catch (Exception e) {
            log.debug("Could not parse URL, treating as host: {}", url);
            // If parsing fails, treat the whole string as the host
            return url.split("/")[0].split(":")[0];
        }
    }

    private String normalizeHost(String host) {
        if (host == null)
            return "";
        // Remove www. prefix and lowercase
        String normalized = host.toLowerCase().trim();
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }

    private boolean isSelfHost(String normalizedHost) {
        // Check exact match against self-protection list
        if (guardProperties.getSelfProtectionHostsSet().contains(normalizedHost)) {
            return true;
        }
        // Check if it's a subdomain of ai2qa.com
        return normalizedHost.endsWith(".ai2qa.com") || normalizedHost.equals("ai2qa.com");
    }

    private boolean isLocalhost(String host) {
        return host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("0.0.0.0") ||
                host.startsWith("192.168.") ||
                host.startsWith("10.") ||
                host.equals("::1");
    }

    private java.util.Optional<String> findBlacklistMatch(String host) {
        for (String entry : blacklist) {
            if (entry.startsWith(".")) {
                // TLD/suffix match (e.g., ".gov")
                if (host.endsWith(entry)) {
                    return java.util.Optional.of(entry);
                }
            } else if (host.equals(entry) || host.endsWith("." + entry)) {
                // Exact match or subdomain match
                return java.util.Optional.of(entry);
            } else if (host.contains(entry)) {
                // Substring match (for keywords)
                return java.util.Optional.of(entry);
            }
        }
        return java.util.Optional.empty();
    }

    private List<String> parseBlacklist(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<String> parseAllowlist(String config) {
        if (config == null || config.isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Checks if a host is in the allowlist.
     * Supports:
     * - Exact IP match: "192.168.1.2"
     * - Exact domain match: "example.com"
     * - Subdomain match: "sub.example.com" matches allowlist entry "example.com"
     * - TLD match: ".local" matches "myhost.local"
     *
     * @param host The normalized host to check
     * @return true if host is allowlisted
     */
    private boolean isAllowlisted(String host) {
        for (String entry : allowlist) {
            if (entry.startsWith(".")) {
                // TLD/suffix match (e.g., ".local")
                if (host.endsWith(entry)) {
                    return true;
                }
            } else if (host.equals(entry)) {
                // Exact match (works for IPs like "192.168.1.2")
                return true;
            } else if (host.endsWith("." + entry)) {
                // Subdomain match (e.g., "sub.example.com" matches "example.com")
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the host ends with a blocked TLD.
     * Returns the matched TLD if found.
     */
    private Optional<String> findBlockedTld(String host) {
        if (host == null) return Optional.empty();
        for (String tld : guardProperties.getBlockedTlds()) {
            if (host.endsWith(tld)) {
                return Optional.of(tld);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the host matches a blocked domain.
     * Matches exact domain or subdomains (e.g., "www.google.com" matches "google.com").
     */
    private Optional<String> findBlockedDomain(String host) {
        if (host == null) return Optional.empty();
        for (String domain : guardProperties.getBlockedDomains()) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return Optional.of(domain);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the URL path matches a blocked pattern.
     * Returns a description of the matched pattern if found.
     */
    private Optional<String> findBlockedUrlPattern(String url) {
        if (url == null) return Optional.empty();
        try {
            String normalizedUrl = url;
            if (!url.contains("://")) {
                normalizedUrl = "https://" + url;
            }
            URI uri = new URI(normalizedUrl);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return Optional.empty();
            }

            for (Pattern pattern : guardProperties.getCompiledBlockedUrlPatterns()) {
                if (pattern.matcher(path).find()) {
                    // Return a human-readable description based on the pattern
                    String patternStr = pattern.pattern();
                    if (patternStr.contains("admin")) return Optional.of("admin panel");
                    if (patternStr.contains("login") || patternStr.contains("signin")) return Optional.of("login");
                    if (patternStr.contains("auth") || patternStr.contains("oauth")) return Optional.of("authentication");
                    if (patternStr.contains("env") || patternStr.contains("git") ||
                        patternStr.contains("aws") || patternStr.contains("ssh")) return Optional.of("sensitive configuration");
                    if (patternStr.contains("phpinfo") || patternStr.contains("server-status")) return Optional.of("server diagnostics");
                    return Optional.of("restricted");
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse URL for pattern matching: {}", url);
        }
        return Optional.empty();
    }
}
