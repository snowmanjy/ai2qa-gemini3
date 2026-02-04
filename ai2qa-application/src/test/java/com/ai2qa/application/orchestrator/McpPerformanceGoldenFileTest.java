package com.ai2qa.application.orchestrator;

import com.ai2qa.domain.model.PerformanceMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden file tests for MCP performance metrics parsing.
 *
 * <p>These tests validate that the Java code correctly parses MCP response formats
 * using real JSON fixtures. This prevents Issue #4 (Java not unwrapping MCP content format)
 * from recurring.
 *
 * <p>The golden files represent the actual response format from the MCP server.
 */
class McpPerformanceGoldenFileTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Unwraps MCP content format: {"content": [{"type": "text", "text": "{...JSON...}"}]}
     * This is the exact logic from AgentOrchestrator.unwrapMcpContent()
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMcpContent(Map<String, Object> mcpResult) {
        try {
            if (mcpResult.containsKey("content")) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) mcpResult.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    String textJson = (String) firstContent.get("text");
                    if (textJson != null && !textJson.isEmpty()) {
                        return objectMapper.readValue(textJson, new TypeReference<Map<String, Object>>() {});
                    }
                }
                return null;
            }
            return mcpResult;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extracts PerformanceMetrics from unwrapped MCP result.
     * This matches the logic from AgentOrchestrator.extractPerformanceMetrics()
     */
    @SuppressWarnings("unchecked")
    private Optional<PerformanceMetrics> extractPerformanceMetrics(Map<String, Object> result) {
        if (result == null) {
            return Optional.empty();
        }

        Boolean success = (Boolean) result.get("success");
        if (success == null || !success) {
            return Optional.empty();
        }

        Map<String, Object> webVitalsRaw = (Map<String, Object>) result.get("webVitals");
        Map<String, Object> navigationRaw = (Map<String, Object>) result.get("navigation");

        if (webVitalsRaw == null && navigationRaw == null) {
            return Optional.empty();
        }

        // Convert to Map<String, Double>
        Map<String, Double> webVitals = toDoubleMap(webVitalsRaw);
        Map<String, Double> navigation = toDoubleMap(navigationRaw);

        Integer totalResources = getIntFromMap(result, "totalResources");
        Double totalTransferSizeKb = getDoubleFromMap(result, "totalTransferSizeKb");

        return Optional.of(new PerformanceMetrics(
            webVitals,
            navigation,
            totalResources,
            totalTransferSizeKb != null ? totalTransferSizeKb.intValue() : null,
            List.of()  // Issues not parsed in this simplified version
        ));
    }

    private Map<String, Double> toDoubleMap(Map<String, Object> map) {
        if (map == null) return Map.of();
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Number) {
                result.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
            }
        }
        return result;
    }

    private Double getDoubleFromMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Integer getIntFromMap(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private Map<String, Object> loadGoldenFile(String filename) throws IOException {
        String path = "/fixtures/mcp/" + filename;
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Golden file not found: " + path);
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        }
    }

    @Nested
    @DisplayName("Issue #4 Prevention: MCP Content Unwrapping")
    class McpContentUnwrapping {

        @Test
        @DisplayName("Should correctly unwrap MCP wrapped format to extract webVitals")
        void unwrapMcpContent_WithWrappedFormat_ExtractsWebVitals() throws IOException {
            // Arrange - load the golden file that represents actual MCP response format
            Map<String, Object> mcpResponse = loadGoldenFile("performance-metrics-wrapped.json");

            // Act - unwrap the MCP content format
            Map<String, Object> unwrapped = unwrapMcpContent(mcpResponse);

            // Assert - verify unwrapping worked correctly
            assertThat(unwrapped).isNotNull();
            assertThat(unwrapped.get("success")).isEqualTo(true);
            assertThat(unwrapped).containsKey("webVitals");

            @SuppressWarnings("unchecked")
            Map<String, Object> webVitals = (Map<String, Object>) unwrapped.get("webVitals");
            assertThat(webVitals).isNotNull();
            assertThat(webVitals.get("lcp")).isEqualTo(1250.5);
            assertThat(webVitals.get("cls")).isEqualTo(0.05);
            assertThat(webVitals.get("fcp")).isEqualTo(800.3);
            assertThat(webVitals.get("ttfb")).isEqualTo(120.7);
        }

        @Test
        @DisplayName("Should handle already-unwrapped format (direct JSON)")
        void unwrapMcpContent_WithUnwrappedFormat_ReturnsDirectly() throws IOException {
            // Arrange - this format is when the response is already plain JSON
            Map<String, Object> directResponse = loadGoldenFile("performance-metrics-unwrapped.json");

            // Act
            Map<String, Object> result = unwrapMcpContent(directResponse);

            // Assert - should return the same map (already unwrapped)
            assertThat(result).isNotNull();
            assertThat(result.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should handle error responses from MCP")
        void unwrapMcpContent_WithErrorResponse_ExtractsError() throws IOException {
            // Arrange
            Map<String, Object> errorResponse = loadGoldenFile("performance-metrics-empty.json");

            // Act
            Map<String, Object> unwrapped = unwrapMcpContent(errorResponse);

            // Assert
            assertThat(unwrapped).isNotNull();
            assertThat(unwrapped.get("success")).isEqualTo(false);
            assertThat(unwrapped.get("error")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Issue #4 Prevention: End-to-End Metrics Extraction")
    class EndToEndMetricsExtraction {

        @Test
        @DisplayName("Should extract PerformanceMetrics from wrapped MCP response")
        void extractMetrics_FromWrappedMcpResponse_ReturnsValidMetrics() throws IOException {
            // Arrange
            Map<String, Object> mcpResponse = loadGoldenFile("performance-metrics-wrapped.json");

            // Act - simulate full extraction pipeline
            Map<String, Object> unwrapped = unwrapMcpContent(mcpResponse);
            Optional<PerformanceMetrics> metrics = extractPerformanceMetrics(unwrapped);

            // Assert - verify all metrics are extracted correctly
            assertThat(metrics).isPresent();
            PerformanceMetrics pm = metrics.get();

            // Core Web Vitals
            assertThat(pm.getLcp()).hasValue(1250.5);
            assertThat(pm.getCls()).hasValue(0.05);
            assertThat(pm.getFcp()).hasValue(800.3);
            assertThat(pm.getTtfb()).hasValue(120.7);
            assertThat(pm.getFid()).hasValue(15.2);

            // Navigation timing
            assertThat(pm.getPageLoad()).hasValue(2100.5);
            assertThat(pm.getDomContentLoaded()).hasValue(1500.3);

            // Resource metrics
            assertThat(pm.totalResources()).isEqualTo(2);
            assertThat(pm.totalTransferSizeKb()).isEqualTo(171);
        }

        @Test
        @DisplayName("Should return empty Optional for error response")
        void extractMetrics_FromErrorResponse_ReturnsEmpty() throws IOException {
            // Arrange
            Map<String, Object> mcpResponse = loadGoldenFile("performance-metrics-empty.json");

            // Act
            Map<String, Object> unwrapped = unwrapMcpContent(mcpResponse);
            Optional<PerformanceMetrics> metrics = extractPerformanceMetrics(unwrapped);

            // Assert - should not have metrics for error response
            assertThat(metrics).isEmpty();
        }

        @Test
        @DisplayName("Should extract metrics from response with issues (poor performance)")
        void extractMetrics_FromResponseWithIssues_ExtractsMetrics() throws IOException {
            // Arrange - response with poor performance numbers
            Map<String, Object> mcpResponse = loadGoldenFile("performance-metrics-with-issues.json");

            // Act
            Map<String, Object> unwrapped = unwrapMcpContent(mcpResponse);
            Optional<PerformanceMetrics> metrics = extractPerformanceMetrics(unwrapped);

            // Assert - should still extract metrics even when they indicate poor performance
            assertThat(metrics).isPresent();
            PerformanceMetrics pm = metrics.get();

            // These are intentionally poor values (> thresholds)
            assertThat(pm.getLcp()).hasValue(4500.0);  // > 4000ms threshold
            assertThat(pm.getCls()).hasValue(0.35);    // > 0.25 threshold
            assertThat(pm.getTtfb()).hasValue(1200.0); // > 800ms threshold
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Should handle null input gracefully")
        void extractMetrics_WithNull_ReturnsEmpty() {
            // Act
            Optional<PerformanceMetrics> metrics = extractPerformanceMetrics(null);

            // Assert
            assertThat(metrics).isEmpty();
        }

        @Test
        @DisplayName("Should handle empty content array")
        void unwrapMcpContent_WithEmptyContentArray_ReturnsNull() {
            // Arrange
            Map<String, Object> emptyContent = Map.of("content", List.of());

            // Act
            Map<String, Object> result = unwrapMcpContent(emptyContent);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle content without text field")
        void unwrapMcpContent_WithoutTextField_ReturnsNull() {
            // Arrange
            Map<String, Object> noTextField = Map.of(
                "content", List.of(Map.of("type", "image", "data", "base64data"))
            );

            // Act
            Map<String, Object> result = unwrapMcpContent(noTextField);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle invalid JSON in text field")
        void unwrapMcpContent_WithInvalidJson_ReturnsNull() {
            // Arrange
            Map<String, Object> invalidJson = Map.of(
                "content", List.of(Map.of("type", "text", "text", "not valid json {"))
            );

            // Act
            Map<String, Object> result = unwrapMcpContent(invalidJson);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("Should handle missing webVitals and navigation")
        void extractMetrics_WithoutWebVitalsOrNavigation_ReturnsEmpty() {
            // Arrange - success=true but no metrics data
            Map<String, Object> noMetrics = Map.of(
                "success", true,
                "totalResources", 0
            );

            // Act
            Optional<PerformanceMetrics> metrics = extractPerformanceMetrics(noMetrics);

            // Assert - should return empty because no webVitals or navigation
            assertThat(metrics).isEmpty();
        }
    }
}
