package com.ai2qa.mcp.tools;

import com.ai2qa.mcp.McpClient;

import java.util.Map;
import java.util.Optional;

/**
 * Tool for browser navigation operations.
 */
public class NavigateTool {

    private static final String TOOL_NAME = "navigate_page";

    private final McpClient mcpClient;

    public NavigateTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Navigates to a URL.
     *
     * @param url The URL to navigate to
     * @return Result containing page title and URL
     */
    public Result navigateTo(String url) {
        return navigateTo(url, null);
    }

    /**
     * Navigates to a URL with optional timeout.
     *
     * @param url       The URL to navigate to
     * @param timeoutMs Optional timeout in milliseconds
     * @return Result containing page title and URL
     */
    public Result navigateTo(String url, Integer timeoutMs) {
        var params = new java.util.HashMap<String, Object>();
        params.put("url", url);
        params.put("type", "url");
        if (timeoutMs != null) {
            params.put("timeout", timeoutMs);
        }

        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, params);
        return Result.from(response);
    }

    /**
     * Navigates back in browser history.
     */
    public Result navigateBack() {
        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, Map.of("type", "back"));
        return Result.from(response);
    }

    /**
     * Navigates forward in browser history.
     */
    public Result navigateForward() {
        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, Map.of("type", "forward"));
        return Result.from(response);
    }

    /**
     * Reloads the current page.
     *
     * @param ignoreCache Whether to ignore cache during reload
     */
    public Result reload(boolean ignoreCache) {
        var params = new java.util.HashMap<String, Object>();
        params.put("type", "reload");
        if (ignoreCache) {
            params.put("ignoreCache", true);
        }

        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, params);
        return Result.from(response);
    }

    /**
     * Reloads the current page (uses cache).
     */
    public Result reload() {
        return reload(false);
    }

    /**
     * Navigation result.
     */
    public record Result(
            String url,
            String title,
            boolean success,
            Optional<String> error
    ) {
        @SuppressWarnings("unchecked")
        static Result from(Map<String, Object> response) {
            // Extract from MCP response format
            var content = (java.util.List<Map<String, Object>>) response.get("content");
            if (content != null && !content.isEmpty()) {
                var firstContent = content.get(0);
                String text = (String) firstContent.get("text");
                // Parse the text response to extract URL and title if available
                return new Result(
                        extractField(text, "url"),
                        extractField(text, "title"),
                        true,
                        Optional.empty()
                );
            }

            // Direct response format
            return new Result(
                    getString(response, "url"),
                    getString(response, "title"),
                    !response.containsKey("error"),
                    Optional.ofNullable((String) response.get("error"))
            );
        }

        private static String extractField(String text, String field) {
            // Simple extraction - could be improved with regex or JSON parsing
            if (text == null) {
                return "";
            }
            return text;
        }

        private static String getString(Map<String, Object> response, String key) {
            return Optional.ofNullable((String) response.get(key)).orElse("");
        }
    }
}
