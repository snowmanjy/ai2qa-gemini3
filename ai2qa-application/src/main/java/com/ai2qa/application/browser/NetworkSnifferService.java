package com.ai2qa.application.browser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for capturing HTTP 4xx/5xx network errors during test execution.
 *
 * <p>This service provides thread-safe collection of network errors that occur
 * during browser interactions, enabling The Healer to diagnose frontend vs backend issues.
 *
 * <p>Usage Pattern:
 * <pre>
 * NetworkSnifferService.ErrorCollector collector = sniffer.startCapturing();
 * // ... execute browser actions ...
 * List<String> errors = collector.getErrors();
 * </pre>
 */
@Service
public class NetworkSnifferService {

    private static final Logger log = LoggerFactory.getLogger(NetworkSnifferService.class);

    /**
     * Creates a new error collector for capturing network errors during a step.
     *
     * @return Thread-safe error collector
     */
    public ErrorCollector startCapturing() {
        return new ErrorCollector();
    }

    /**
     * Formats a network error from status code and URL.
     */
    public static String formatError(int statusCode, String method, String url) {
        String category = categorizeError(statusCode);
        String truncatedUrl = truncateUrl(url, 100);
        return String.format("[%d %s] %s %s", statusCode, category, method, truncatedUrl);
    }

    /**
     * Categorizes the error based on status code.
     */
    private static String categorizeError(int statusCode) {
        return switch (statusCode / 100) {
            case 4 -> statusCode == 401 ? "Unauthorized" :
                      statusCode == 403 ? "Forbidden" :
                      statusCode == 404 ? "Not Found" :
                      statusCode == 429 ? "Rate Limited" : "Client Error";
            case 5 -> statusCode == 500 ? "Internal Server Error" :
                      statusCode == 502 ? "Bad Gateway" :
                      statusCode == 503 ? "Service Unavailable" :
                      statusCode == 504 ? "Gateway Timeout" : "Server Error";
            default -> "Error";
        };
    }

    /**
     * Truncates URL for logging.
     */
    private static String truncateUrl(String url, int maxLength) {
        if (url == null) return "";
        if (url.length() <= maxLength) return url;
        return url.substring(0, maxLength - 3) + "...";
    }

    /**
     * Thread-safe collector for network errors during a single step execution.
     */
    public static class ErrorCollector {
        private final List<String> errors = new CopyOnWriteArrayList<>();
        private volatile boolean capturing = true;

        /**
         * Records a network error.
         */
        public void recordError(int statusCode, String method, String url) {
            if (capturing && statusCode >= 400) {
                String error = formatError(statusCode, method, url);
                errors.add(error);
                log.debug("Captured network error: {}", error);
            }
        }

        /**
         * Records a formatted error string.
         */
        public void recordError(String formattedError) {
            if (capturing) {
                errors.add(formattedError);
            }
        }

        /**
         * Stops capturing and returns collected errors.
         */
        public List<String> stopAndGetErrors() {
            capturing = false;
            return new ArrayList<>(errors);
        }

        /**
         * Returns current errors without stopping.
         */
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }

        /**
         * Returns true if any errors were captured.
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * Returns count of captured errors.
         */
        public int errorCount() {
            return errors.size();
        }
    }

    /**
     * Analyzes network errors and provides context for The Healer.
     */
    public String analyzeForHealer(List<String> networkErrors) {
        if (networkErrors == null || networkErrors.isEmpty()) {
            return "";
        }

        StringBuilder analysis = new StringBuilder();
        analysis.append("\n\n[NETWORK ERRORS DETECTED]\n");
        analysis.append("The following HTTP errors were captured during this step. ");
        analysis.append("Use this to diagnose if the issue is Frontend vs Backend:\n\n");

        int serverErrors = 0;
        int clientErrors = 0;
        int authErrors = 0;

        for (String error : networkErrors) {
            analysis.append("• ").append(error).append("\n");
            
            // Categorize
            if (error.contains("500") || error.contains("502") || 
                error.contains("503") || error.contains("504") ||
                error.contains("Server Error")) {
                serverErrors++;
            } else if (error.contains("401") || error.contains("403") ||
                       error.contains("Unauthorized") || error.contains("Forbidden")) {
                authErrors++;
            } else {
                clientErrors++;
            }
        }

        // Add summary
        analysis.append("\n📊 Summary:\n");
        if (serverErrors > 0) {
            analysis.append("• ⚠️ ").append(serverErrors)
                    .append(" server error(s) - BLAME THE BACKEND, not the UI.\n");
        }
        if (authErrors > 0) {
            analysis.append("• 🔐 ").append(authErrors)
                    .append(" auth error(s) - Check session/token validity.\n");
        }
        if (clientErrors > 0) {
            analysis.append("• 📱 ").append(clientErrors)
                    .append(" client error(s) - Check request parameters.\n");
        }

        return analysis.toString();
    }
}
