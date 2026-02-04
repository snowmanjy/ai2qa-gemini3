package com.ai2qa.mcp.tools;

import com.ai2qa.mcp.McpClient;

import java.util.Map;
import java.util.Optional;

/**
 * Tool for clicking on page elements.
 */
public class ClickTool {

    private static final String TOOL_NAME = "click";

    private final McpClient mcpClient;

    public ClickTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Clicks on an element by its selector.
     *
     * @param selector The element selector from the page snapshot
     * @return Click result
     */
    public Result click(String selector) {
        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, Map.of("selector", selector));
        return Result.from(response);
    }

    /**
     * Double-clicks on an element by its selector.
     *
     * @param selector The element selector from the page snapshot
     * @return Click result
     */
    public Result doubleClick(String selector) {
        Map<String, Object> response = mcpClient.callTool(
                TOOL_NAME,
                Map.of("selector", selector, "dblClick", true)
        );
        return Result.from(response);
    }

    /**
     * Click result.
     */
    public record Result(
            boolean success,
            Optional<String> error
    ) {
        @SuppressWarnings("unchecked")
        static Result from(Map<String, Object> response) {
            var content = (java.util.List<Map<String, Object>>) response.get("content");
            if (content != null && !content.isEmpty()) {
                var firstContent = content.get(0);
                String text = (String) firstContent.get("text");
                boolean hasError = text != null && text.toLowerCase().contains("error");
                return new Result(!hasError, hasError ? Optional.of(text) : Optional.empty());
            }

            boolean hasError = response.containsKey("error");
            return new Result(
                    !hasError,
                    hasError ? Optional.of(String.valueOf(response.get("error"))) : Optional.empty()
            );
        }
    }
}
