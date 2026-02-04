package com.ai2qa.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for MCP tools with convenience methods for common browser operations.
 *
 * <p>Provides a type-safe layer over the raw MCP client tool calls.
 */
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final McpClient mcpClient;
    private final Map<String, ToolInfo> toolCache = new ConcurrentHashMap<>();

    public McpToolRegistry(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Refreshes the tool cache from the MCP server.
     */
    public void refresh() {
        List<Map<String, Object>> tools = mcpClient.listTools();
        toolCache.clear();

        for (Map<String, Object> tool : tools) {
            String name = (String) tool.get("name");
            String description = (String) tool.get("description");
            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) tool.get("inputSchema");

            toolCache.put(name, new ToolInfo(name, description, inputSchema));
            log.debug("Registered tool: {}", name);
        }

        log.info("Registered {} tools from MCP server", toolCache.size());
    }

    /**
     * Gets information about a specific tool.
     */
    public Optional<ToolInfo> getToolInfo(String name) {
        return Optional.ofNullable(toolCache.get(name));
    }

    /**
     * Lists all registered tools.
     */
    public List<ToolInfo> listTools() {
        return List.copyOf(toolCache.values());
    }

    // ==================== Browser Navigation Tools ====================

    /**
     * Navigates to a URL.
     *
     * @param url The URL to navigate to
     * @return Navigation result containing page info
     */
    public NavigationResult navigateTo(String url) {
        Map<String, Object> result = mcpClient.callTool(
                "navigate_page",
                Map.of("url", url, "type", "url")
        );
        return new NavigationResult(result);
    }

    /**
     * Navigates back in browser history.
     */
    public NavigationResult navigateBack() {
        Map<String, Object> result = mcpClient.callTool(
                "navigate_page",
                Map.of("type", "back")
        );
        return new NavigationResult(result);
    }

    /**
     * Navigates forward in browser history.
     */
    public NavigationResult navigateForward() {
        Map<String, Object> result = mcpClient.callTool(
                "navigate_page",
                Map.of("type", "forward")
        );
        return new NavigationResult(result);
    }

    /**
     * Reloads the current page.
     */
    public NavigationResult reload() {
        Map<String, Object> result = mcpClient.callTool(
                "navigate_page",
                Map.of("type", "reload")
        );
        return new NavigationResult(result);
    }

    // ==================== Interaction Tools ====================

    /**
     * Clicks on an element by selector.
     *
     * @param selector The element selector from snapshot
     * @return Click result
     */
    public ClickResult click(String selector) {
        Map<String, Object> result = mcpClient.callTool(
                "click",
                Map.of("selector", selector)
        );
        return new ClickResult(result);
    }

    /**
     * Double-clicks on an element by selector.
     *
     * @param selector The element selector from snapshot
     * @return Click result
     */
    public ClickResult doubleClick(String selector) {
        Map<String, Object> result = mcpClient.callTool(
                "click",
                Map.of("selector", selector, "dblClick", true)
        );
        return new ClickResult(result);
    }

    /**
     * Types text into an input element.
     *
     * @param selector The element selector from snapshot
     * @param value    The text to type
     * @return Fill result
     */
    public FillResult fill(String selector, String value) {
        Map<String, Object> result = mcpClient.callTool(
                "fill",
                Map.of("selector", selector, "value", value)
        );
        return new FillResult(result);
    }

    /**
     * Hovers over an element.
     *
     * @param selector The element selector from snapshot
     * @return Hover result
     */
    public HoverResult hover(String selector) {
        Map<String, Object> result = mcpClient.callTool(
                "hover",
                Map.of("selector", selector)
        );
        return new HoverResult(result);
    }

    /**
     * Presses a key or key combination.
     *
     * @param key The key to press (e.g., "Enter", "Control+A")
     * @return Key press result
     */
    public KeyPressResult pressKey(String key) {
        Map<String, Object> result = mcpClient.callTool(
                "press_key",
                Map.of("key", key)
        );
        return new KeyPressResult(result);
    }

    // ==================== Snapshot Tools ====================

    /**
     * Takes a DOM snapshot of the current page.
     *
     * @return Snapshot result with element UIDs
     */
    public SnapshotResult takeSnapshot() {
        Map<String, Object> result = mcpClient.callTool(
                "take_snapshot",
                Map.of()
        );
        return new SnapshotResult(result);
    }

    /**
     * Takes a verbose DOM snapshot with full accessibility tree.
     *
     * @return Snapshot result with detailed element info
     */
    public SnapshotResult takeVerboseSnapshot() {
        Map<String, Object> result = mcpClient.callTool(
                "take_snapshot",
                Map.of("verbose", true)
        );
        return new SnapshotResult(result);
    }

    /**
     * Takes a screenshot of the current page.
     *
     * @return Screenshot result with base64 image data
     */
    public ScreenshotResult takeScreenshot() {
        Map<String, Object> result = mcpClient.callTool(
                "take_screenshot",
                Map.of()
        );
        return new ScreenshotResult(result);
    }

    /**
     * Takes a full page screenshot.
     *
     * @return Screenshot result with base64 image data
     */
    public ScreenshotResult takeFullPageScreenshot() {
        Map<String, Object> result = mcpClient.callTool(
                "take_screenshot",
                Map.of("fullPage", true)
        );
        return new ScreenshotResult(result);
    }

    // ==================== Result Types ====================

    /**
     * Tool information.
     */
    public record ToolInfo(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    /**
     * Navigation result.
     */
    public record NavigationResult(Map<String, Object> raw) {
        public String url() {
            return (String) raw.get("url");
        }

        public String title() {
            return (String) raw.get("title");
        }
    }

    /**
     * Click result.
     */
    public record ClickResult(Map<String, Object> raw) {
        public boolean success() {
            return raw.containsKey("success") && Boolean.TRUE.equals(raw.get("success"));
        }
    }

    /**
     * Fill result.
     */
    public record FillResult(Map<String, Object> raw) {
        public boolean success() {
            return raw.containsKey("success") && Boolean.TRUE.equals(raw.get("success"));
        }
    }

    /**
     * Hover result.
     */
    public record HoverResult(Map<String, Object> raw) {
        public boolean success() {
            return raw.containsKey("success") && Boolean.TRUE.equals(raw.get("success"));
        }
    }

    /**
     * Key press result.
     */
    public record KeyPressResult(Map<String, Object> raw) {
        public boolean success() {
            return raw.containsKey("success") && Boolean.TRUE.equals(raw.get("success"));
        }
    }

    /**
     * Snapshot result containing DOM structure.
     */
    public record SnapshotResult(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> content() {
            return (List<Map<String, Object>>) raw.get("content");
        }

        public String text() {
            var content = content();
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
            return "";
        }
    }

    /**
     * Screenshot result.
     */
    public record ScreenshotResult(Map<String, Object> raw) {
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> content() {
            return (List<Map<String, Object>>) raw.get("content");
        }

        public String base64Image() {
            var content = content();
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("data");
            }
            return "";
        }

        public String mimeType() {
            var content = content();
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("mimeType");
            }
            return "image/png";
        }
    }
}
