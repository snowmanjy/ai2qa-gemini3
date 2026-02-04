package com.ai2qa.domain.model.knowledge;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Factory for creating valid SitePattern instances.
 */
public final class SitePatternFactory {

    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );
    private static final int MAX_KEY_LENGTH = 255;
    private static final int MAX_VALUE_LENGTH = 10000;

    private SitePatternFactory() {
    }

    /**
     * Creates a new SitePattern with validation.
     *
     * @param domain    The domain (e.g., "app.example.com")
     * @param type      The pattern type
     * @param key       The pattern key (e.g., "login_button")
     * @param value     The pattern value (e.g., CSS selector)
     * @param runId     Optional test run that discovered this pattern
     * @param tenantId  Optional tenant for TENANT/PRIVATE visibility
     * @param visibility The visibility level
     * @return Optional containing the pattern if valid, empty otherwise
     */
    public static Optional<SitePattern> create(
            String domain,
            PatternType type,
            String key,
            String value,
            UUID runId,
            String tenantId,
            Visibility visibility) {

        return validateDomain(domain)
                .flatMap(d -> validateKey(key))
                .flatMap(k -> validateValue(value))
                .flatMap(v -> validateVisibilityTenant(visibility, tenantId))
                .map(vt -> new SitePattern(
                        SitePatternId.generate(),
                        sanitizeDomain(domain),
                        type,
                        sanitizeKey(key),
                        value,
                        new BigDecimal("0.50"),
                        0,
                        0,
                        Optional.empty(),
                        Optional.of(Instant.now()),
                        Instant.now(),
                        Optional.ofNullable(runId),
                        visibility,
                        Optional.ofNullable(tenantId),
                        0
                ));
    }

    /**
     * Creates a simple global pattern with default settings.
     */
    public static Optional<SitePattern> createGlobal(
            String domain,
            PatternType type,
            String key,
            String value,
            UUID runId) {
        return create(domain, type, key, value, runId, null, Visibility.GLOBAL);
    }

    /**
     * Reconstitutes a SitePattern from database storage.
     */
    public static SitePattern reconstitute(
            UUID id,
            String domain,
            PatternType type,
            String key,
            String value,
            BigDecimal confidenceScore,
            int successCount,
            int failureCount,
            Integer avgDurationMs,
            Instant lastSeenAt,
            Instant createdAt,
            UUID createdByRunId,
            Visibility visibility,
            String tenantId,
            int version) {

        return new SitePattern(
                SitePatternId.reconstitute(id),
                domain,
                type,
                key,
                value,
                confidenceScore,
                successCount,
                failureCount,
                Optional.ofNullable(avgDurationMs),
                Optional.ofNullable(lastSeenAt),
                createdAt,
                Optional.ofNullable(createdByRunId),
                visibility,
                Optional.ofNullable(tenantId),
                version
        );
    }

    private static Optional<String> validateDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        String sanitized = sanitizeDomain(domain);
        if (!DOMAIN_PATTERN.matcher(sanitized).matches() && !sanitized.equals("localhost")) {
            return Optional.empty();
        }
        return Optional.of(sanitized);
    }

    private static Optional<String> validateKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(sanitizeKey(key));
    }

    private static Optional<String> validateValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Optional<Boolean> validateVisibilityTenant(Visibility visibility, String tenantId) {
        if (visibility == Visibility.TENANT || visibility == Visibility.PRIVATE) {
            if (tenantId == null || tenantId.isBlank()) {
                return Optional.empty();
            }
        }
        return Optional.of(true);
    }

    private static String sanitizeDomain(String domain) {
        return domain.toLowerCase().trim();
    }

    private static String sanitizeKey(String key) {
        return key.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .substring(0, Math.min(key.length(), MAX_KEY_LENGTH));
    }
}
