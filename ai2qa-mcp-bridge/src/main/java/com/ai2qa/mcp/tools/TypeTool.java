package com.ai2qa.mcp.tools;

import com.ai2qa.mcp.McpClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for typing text into page elements.
 */
public class TypeTool {

    private static final String FILL_TOOL = "fill";
    private static final String FILL_FORM_TOOL = "fill_form";
    private static final String PRESS_KEY_TOOL = "press_key";

    private final McpClient mcpClient;

    public TypeTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Fills a value into an input element.
     *
     * @param selector The element selector from the page snapshot
     * @param value    The value to fill
     * @return Fill result
     */
    public Result fill(String selector, String value) {
        Map<String, Object> response = mcpClient.callTool(
                FILL_TOOL,
                Map.of("selector", selector, "value", value)
        );
        return Result.from(response);
    }

    /**
     * Fills multiple form elements at once.
     *
     * @param elements List of element entries (selector and value pairs)
     * @return Fill result
     */
    public Result fillForm(List<FormElement> elements) {
        List<Map<String, String>> elementMaps = elements.stream()
            .map(e -> Map.of("selector", e.selector(), "value", e.value()))
                .toList();

        Map<String, Object> response = mcpClient.callTool(
                FILL_FORM_TOOL,
                Map.of("elements", elementMaps)
        );
        return Result.from(response);
    }

    /**
     * Presses a key or key combination.
     *
     * @param key The key to press (e.g., "Enter", "Control+A", "Tab")
     * @return Key press result
     */
    public Result pressKey(String key) {
        Map<String, Object> response = mcpClient.callTool(
                PRESS_KEY_TOOL,
                Map.of("key", key)
        );
        return Result.from(response);
    }

    /**
     * Form element for batch filling.
     */
    public record FormElement(String selector, String value) {

        /**
         * Creates a form element entry.
         */
        public static FormElement of(String selector, String value) {
            return new FormElement(selector, value);
        }
    }

    /**
     * Type/fill operation result.
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
