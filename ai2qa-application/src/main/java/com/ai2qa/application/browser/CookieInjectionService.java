package com.ai2qa.application.browser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for parsing and validating cookies for browser injection.
 *
 * <p>Converts EditThisCookie JSON format to a normalized format
 * that can be passed to the MCP browser bridge for injection.
 */
@Service
public class CookieInjectionService {

    private static final Logger log = LoggerFactory.getLogger(CookieInjectionService.class);
    
    private final ObjectMapper objectMapper;

    public CookieInjectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses and validates the cookie JSON.
     *
     * @param cookiesJson JSON string of cookies (EditThisCookie format)
     * @return List of normalized cookie maps ready for browser injection, or empty list if invalid
     */
    public List<Map<String, Object>> parseCookies(String cookiesJson) {
        if (cookiesJson == null || cookiesJson.isBlank()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawCookies = objectMapper.readValue(
                    cookiesJson,
                    new TypeReference<>() {});

            List<Map<String, Object>> normalized = rawCookies.stream()
                    .filter(this::isValidCookie)
                    .map(this::normalizeCookie)
                    .toList();

            log.info("Parsed {} cookies for authenticated testing", normalized.size());
            return normalized;

        } catch (Exception e) {
            log.error("Failed to parse cookies JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Checks if cookie has required fields.
     */
    private boolean isValidCookie(Map<String, Object> cookie) {
        return cookie.containsKey("name") && 
               cookie.containsKey("value") &&
               cookie.get("name") != null;
    }

    /**
     * Normalizes a cookie for browser injection.
     * Ensures all required fields have sensible defaults.
     */
    private Map<String, Object> normalizeCookie(Map<String, Object> raw) {
        return Map.of(
                "name", getString(raw, "name", ""),
                "value", getString(raw, "value", ""),
                "domain", getString(raw, "domain", ""),
                "path", getString(raw, "path", "/"),
                "secure", getBoolean(raw, "secure", false),
                "httpOnly", getBoolean(raw, "httpOnly", false),
                "sameSite", getString(raw, "sameSite", "Lax")
        );
    }

    /**
     * Converts cookies to JSON for MCP bridge transmission.
     */
    public String toJson(List<Map<String, Object>> cookies) {
        try {
            return objectMapper.writeValueAsString(cookies);
        } catch (Exception e) {
            log.error("Failed to serialize cookies: {}", e.getMessage());
            return "[]";
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
}
