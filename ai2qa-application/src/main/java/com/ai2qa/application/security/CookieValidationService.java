package com.ai2qa.application.security;

import com.ai2qa.application.exception.ProhibitedTargetException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service to validate cookies before they are used in browser sessions.
 * Prevents SSRF attacks via cookie domain manipulation.
 *
 * Security rules:
 * 1. Cookie domain must match or be a subdomain of the target URL's domain
 * 2. Cookies for internal/metadata domains are always blocked
 * 3. Cookies cannot be set for IP addresses (security best practice)
 */
@Service
public class CookieValidationService {

    private static final Logger log = LoggerFactory.getLogger(CookieValidationService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Domains that should never have cookies set for them.
     * These are internal/metadata endpoints that could be exploited.
     */
    private static final Set<String> BLOCKED_COOKIE_DOMAINS = Set.of(
            // AWS/GCP instance metadata
            "169.254.169.254",
            "metadata.google.internal",
            // AWS ECS metadata
            "169.254.169.253",
            "169.254.170.2",
            // Kubernetes internal DNS
            "kubernetes.default.svc",
            "kubernetes.default",
            "kubernetes.default.svc.cluster.local",
            // Localhost variants
            "localhost",
            "127.0.0.1",
            "0.0.0.0"
    );

    /**
     * Validates cookies against the target URL.
     *
     * @param cookiesJson JSON string containing cookie array
     * @param targetUrl   The URL that will be tested
     * @throws ProhibitedTargetException if any cookie has an invalid or blocked domain
     */
    public void validateCookies(String cookiesJson, String targetUrl) {
        if (cookiesJson == null || cookiesJson.isBlank()) {
            return; // No cookies to validate
        }

        String targetDomain = extractDomain(targetUrl)
                .orElseThrow(() -> new ProhibitedTargetException("invalid-url",
                        "Cannot extract domain from target URL"));

        List<Map<String, Object>> cookies;
        try {
            cookies = objectMapper.readValue(cookiesJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse cookies JSON: {}", e.getMessage());
            throw new ProhibitedTargetException("invalid-cookies",
                    "Invalid cookies JSON format");
        }

        for (Map<String, Object> cookie : cookies) {
            String cookieDomain = Optional.ofNullable(cookie.get("domain"))
                    .map(Object::toString)
                    .map(String::toLowerCase)
                    .map(this::normalizeDomain)
                    .orElse(null);

            String cookieName = Optional.ofNullable(cookie.get("name"))
                    .map(Object::toString)
                    .orElse("(unnamed)");

            if (cookieDomain == null) {
                // Cookie without explicit domain - will use target domain, which is safe
                continue;
            }

            // Check if cookie domain is blocked
            if (isBlockedDomain(cookieDomain)) {
                log.warn("Blocked cookie for internal domain: {} (cookie: {})", cookieDomain, cookieName);
                throw new ProhibitedTargetException(cookieDomain,
                        "Cookie domain is blocked for security reasons: " + cookieDomain);
            }

            // Check if cookie domain matches target domain
            if (!isDomainMatch(cookieDomain, targetDomain)) {
                log.warn("Cookie domain mismatch: cookie={}, target={} (cookie: {})",
                        cookieDomain, targetDomain, cookieName);
                throw new ProhibitedTargetException(cookieDomain,
                        "Cookie domain '" + cookieDomain + "' does not match target domain '" + targetDomain + "'");
            }
        }

        log.debug("Validated {} cookies for target {}", cookies.size(), targetDomain);
    }

    /**
     * Checks if a domain is in the blocked list.
     */
    private boolean isBlockedDomain(String domain) {
        if (domain == null) return false;

        String normalized = domain.toLowerCase().trim();

        // Direct match
        if (BLOCKED_COOKIE_DOMAINS.contains(normalized)) {
            return true;
        }

        // Check for IP address format (no cookies for IPs)
        if (isIpAddress(normalized)) {
            return true;
        }

        // Check for internal TLDs
        if (normalized.endsWith(".internal") ||
            normalized.endsWith(".local") ||
            normalized.endsWith(".localhost") ||
            normalized.endsWith(".svc") ||
            normalized.endsWith(".cluster.local")) {
            return true;
        }

        // Check for link-local range
        if (normalized.startsWith("169.254.")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a string looks like an IP address.
     */
    private boolean isIpAddress(String host) {
        if (host == null) return false;

        // Simple IPv4 check
        if (host.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            return true;
        }

        // IPv6 check (contains colons)
        if (host.contains(":")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if cookie domain matches target domain.
     * Cookie domain can match exactly or be a subdomain.
     *
     * Examples:
     * - cookie=example.com, target=example.com -> MATCH
     * - cookie=.example.com, target=www.example.com -> MATCH
     * - cookie=example.com, target=sub.example.com -> MATCH
     * - cookie=other.com, target=example.com -> NO MATCH
     */
    private boolean isDomainMatch(String cookieDomain, String targetDomain) {
        if (cookieDomain == null || targetDomain == null) {
            return false;
        }

        String normalizedCookie = normalizeDomain(cookieDomain.toLowerCase());
        String normalizedTarget = normalizeDomain(targetDomain.toLowerCase());

        // Exact match
        if (normalizedCookie.equals(normalizedTarget)) {
            return true;
        }

        // Cookie domain is parent of target domain
        // e.g., cookie=example.com, target=sub.example.com
        if (normalizedTarget.endsWith("." + normalizedCookie)) {
            return true;
        }

        // Target domain is parent of cookie domain (more restrictive cookie)
        // e.g., cookie=sub.example.com, target=example.com
        // This is NOT allowed - cookie would not be sent
        return false;
    }

    /**
     * Normalizes a domain by removing leading dots.
     */
    private String normalizeDomain(String domain) {
        if (domain == null) return "";
        String normalized = domain.toLowerCase().trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /**
     * Extracts the domain from a URL.
     */
    private Optional<String> extractDomain(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            String normalizedUrl = url;
            if (!url.contains("://")) {
                normalizedUrl = "https://" + url;
            }
            URI uri = new URI(normalizedUrl);
            String host = uri.getHost();
            return Optional.ofNullable(host).map(String::toLowerCase);
        } catch (Exception e) {
            log.debug("Could not parse URL: {}", url);
            return Optional.empty();
        }
    }
}
