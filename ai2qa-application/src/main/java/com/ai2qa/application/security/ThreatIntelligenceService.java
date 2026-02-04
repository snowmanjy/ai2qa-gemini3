package com.ai2qa.application.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integrates with Google Safe Browsing API to check URLs for malware and phishing.
 *
 * <p>Features:
 * - Checks URLs against Google's constantly updated threat lists
 * - Caches results to reduce API calls (1-hour TTL)
 * - Daily rate limit to stay within free tier (default 9,000/day, free tier is 10,000)
 * - Optional via feature toggle (disabled by default)
 *
 * <p>To enable, set in application.yml:
 * <pre>
 * ai2qa:
 *   security:
 *     safe-browsing:
 *       enabled: true
 *       api-key: YOUR_GOOGLE_API_KEY
 * </pre>
 *
 * <p>Get an API key from: https://console.cloud.google.com/apis/credentials
 * Enable "Safe Browsing API" at: https://console.cloud.google.com/apis/library/safebrowsing.googleapis.com
 */
@Service
public class ThreatIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ThreatIntelligenceService.class);

    private static final String SAFE_BROWSING_API = "https://safebrowsing.googleapis.com/v4/threatMatches:find";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int RATE_LIMIT_WARNING_THRESHOLD = 80; // Warn at 80% of limit
    private static final int MAX_RETRIES = 2;

    private final boolean enabled;
    private final String apiKey;
    private final int dailyRateLimit;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Simple in-memory cache with TTL
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // Daily rate limiting - resets at midnight UTC
    private final AtomicInteger dailyRequestCount = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDay = new AtomicReference<>(LocalDate.now(ZoneOffset.UTC));

    public ThreatIntelligenceService(
            @Value("${ai2qa.security.safe-browsing.enabled:false}") boolean enabled,
            @Value("${ai2qa.security.safe-browsing.api-key:}") String apiKey,
            @Value("${ai2qa.security.safe-browsing.daily-limit:9000}") int dailyRateLimit,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.dailyRateLimit = dailyRateLimit;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        if (enabled && (apiKey == null || apiKey.isBlank())) {
            log.warn("Safe Browsing is enabled but API key is not configured. Feature will be disabled.");
        } else if (enabled) {
            log.info("Safe Browsing integration enabled with daily limit of {} requests", dailyRateLimit);
        }
    }

    /**
     * Checks if a URL is flagged as malicious by Google Safe Browsing.
     *
     * @param url The URL to check
     * @return ThreatResult with threat information if found, or safe result
     */
    public ThreatResult checkUrl(String url) {
        if (!isEnabled()) {
            return ThreatResult.notChecked("Safe Browsing disabled");
        }

        // Check cache first (doesn't count against rate limit)
        CacheEntry cached = cache.get(url);
        if (cached != null && !cached.isExpired()) {
            return cached.result();
        }

        // Check rate limit before making API call
        if (!tryAcquireRateLimit()) {
            log.warn("Safe Browsing daily rate limit ({}) exceeded, skipping check for URL: {}",
                    dailyRateLimit, url);
            return ThreatResult.notChecked("Daily rate limit exceeded");
        }

        try {
            ThreatResult result = queryApiWithRetry(url, MAX_RETRIES);
            cache.put(url, new CacheEntry(result, System.currentTimeMillis() + CACHE_TTL.toMillis()));
            return result;
        } catch (Exception e) {
            log.error("Safe Browsing API error for URL {}: {}", url, e.getMessage());
            return ThreatResult.error("API error: " + e.getMessage());
        }
    }

    /**
     * Queries the Safe Browsing API with retry logic for transient network failures.
     */
    private ThreatResult queryApiWithRetry(String url, int maxRetries) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return queryApi(url);
            } catch (java.io.IOException e) {
                // Retryable network errors
                lastException = e;
                log.warn("Safe Browsing API attempt {} failed for URL {} (retrying): {}",
                        attempt, url, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(500L * attempt); // Backoff: 500ms, 1000ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        throw lastException != null ? lastException : new RuntimeException("Unknown error after retries");
    }

    /**
     * Attempts to acquire a rate limit slot. Resets counter at midnight UTC.
     * @return true if request is allowed, false if rate limit exceeded
     */
    private boolean tryAcquireRateLimit() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate storedDay = currentDay.get();

        // Reset counter if day changed
        if (!today.equals(storedDay)) {
            if (currentDay.compareAndSet(storedDay, today)) {
                int previousCount = dailyRequestCount.getAndSet(0);
                log.info("Safe Browsing daily counter reset. Previous day usage: {}/{}",
                        previousCount, dailyRateLimit);
            }
        }

        int currentCount = dailyRequestCount.incrementAndGet();

        // Log warning when approaching limit
        int warningThreshold = (dailyRateLimit * RATE_LIMIT_WARNING_THRESHOLD) / 100;
        if (currentCount == warningThreshold) {
            log.warn("Safe Browsing API usage at {}% ({}/{} requests). Consider increasing cache TTL.",
                    RATE_LIMIT_WARNING_THRESHOLD, currentCount, dailyRateLimit);
        }

        if (currentCount > dailyRateLimit) {
            dailyRequestCount.decrementAndGet(); // Don't count failed attempts
            return false;
        }

        return true;
    }

    /**
     * Returns current daily request count (for monitoring/testing).
     */
    public int getDailyRequestCount() {
        return dailyRequestCount.get();
    }

    /**
     * Returns configured daily rate limit.
     */
    public int getDailyRateLimit() {
        return dailyRateLimit;
    }

    private ThreatResult queryApi(String url) throws Exception {
        SafeBrowsingRequest requestBody = new SafeBrowsingRequest(
                new ClientInfo("ai2qa", "1.0.0"),
                new ThreatInfo(
                        List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                        List.of("ANY_PLATFORM"),
                        List.of("URL"),
                        List.of(new ThreatEntry(url))
                )
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SAFE_BROWSING_API + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Safe Browsing API returned status {}: {}", response.statusCode(), response.body());
            return ThreatResult.error("API returned status " + response.statusCode());
        }

        SafeBrowsingResponse apiResponse = objectMapper.readValue(response.body(), SafeBrowsingResponse.class);

        if (apiResponse.matches() == null || apiResponse.matches().isEmpty()) {
            return ThreatResult.safe();
        }

        // URL is flagged as malicious
        ThreatMatch match = apiResponse.matches().get(0);
        return ThreatResult.threat(match.threatType(), match.platformType());
    }

    /**
     * Returns true if Safe Browsing is enabled and configured.
     */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /**
     * Clears the cache (for testing).
     */
    public void clearCache() {
        cache.clear();
    }

    // ==================== DTOs ====================

    /**
     * Result of a threat check.
     */
    public record ThreatResult(
            Status status,
            String threatType,
            String platformType,
            String message
    ) {
        public static ThreatResult safe() {
            return new ThreatResult(Status.SAFE, null, null, null);
        }

        public static ThreatResult threat(String threatType, String platformType) {
            return new ThreatResult(Status.THREAT, threatType, platformType,
                    "URL flagged as " + threatType);
        }

        public static ThreatResult notChecked(String reason) {
            return new ThreatResult(Status.NOT_CHECKED, null, null, reason);
        }

        public static ThreatResult error(String message) {
            return new ThreatResult(Status.ERROR, null, null, message);
        }

        public boolean isThreat() {
            return status == Status.THREAT;
        }

        public boolean isSafe() {
            return status == Status.SAFE;
        }

        public enum Status {
            SAFE,
            THREAT,
            NOT_CHECKED,
            ERROR
        }
    }

    // ==================== API Request/Response DTOs ====================

    private record SafeBrowsingRequest(
            @JsonProperty("client") ClientInfo client,
            @JsonProperty("threatInfo") ThreatInfo threatInfo
    ) {}

    private record ClientInfo(
            @JsonProperty("clientId") String clientId,
            @JsonProperty("clientVersion") String clientVersion
    ) {}

    private record ThreatInfo(
            @JsonProperty("threatTypes") List<String> threatTypes,
            @JsonProperty("platformTypes") List<String> platformTypes,
            @JsonProperty("threatEntryTypes") List<String> threatEntryTypes,
            @JsonProperty("threatEntries") List<ThreatEntry> threatEntries
    ) {}

    private record ThreatEntry(
            @JsonProperty("url") String url
    ) {}

    private record SafeBrowsingResponse(
            @JsonProperty("matches") List<ThreatMatch> matches
    ) {}

    private record ThreatMatch(
            @JsonProperty("threatType") String threatType,
            @JsonProperty("platformType") String platformType,
            @JsonProperty("threat") ThreatEntry threat
    ) {}

    private record CacheEntry(ThreatResult result, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
