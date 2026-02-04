package com.ai2qa.mcp.tools;

import com.ai2qa.mcp.McpClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for taking DOM snapshots (accessibility tree) of pages.
 */
public class SnapshotTool {

    private static final String TOOL_NAME = "take_snapshot";

    private final McpClient mcpClient;

    public SnapshotTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Takes a DOM snapshot of the current page.
     *
     * @return Snapshot result with element UIDs
     */
    public Result takeSnapshot() {
        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, Map.of());
        return Result.from(response);
    }

    /**
     * Takes a verbose DOM snapshot with full accessibility tree details.
     *
     * @return Snapshot result with detailed element info
     */
    public Result takeVerboseSnapshot() {
        Map<String, Object> response = mcpClient.callTool(
                TOOL_NAME,
                Map.of("verbose", true)
        );
        return Result.from(response);
    }

    /**
     * Snapshot result containing DOM accessibility tree.
     */
    public record Result(
            boolean success,
            String snapshotText,
            Optional<String> error
    ) {
        @SuppressWarnings("unchecked")
        static Result from(Map<String, Object> response) {
            var content = (List<Map<String, Object>>) response.get("content");

            if (content != null && !content.isEmpty()) {
                var firstContent = content.get(0);
                String text = (String) firstContent.get("text");
                return new Result(true, text != null ? text : "", Optional.empty());
            }

            boolean hasError = response.containsKey("error");
            return new Result(
                    !hasError,
                    "",
                    hasError ? Optional.of(String.valueOf(response.get("error"))) : Optional.empty()
            );
        }

        /**
         * Parses elements from the snapshot text.
         * Format: [uid] role "name" - more details
         */
        public List<Element> parseElements() {
            if (snapshotText == null || snapshotText.isBlank()) {
                return List.of();
            }

            return snapshotText.lines()
                    .filter(line -> line.startsWith("["))
                    .map(Element::parse)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        }
    }

    /**
     * Represents a parsed element from the snapshot.
     */
    public record Element(
            String uid,
            String role,
            String name,
            String details
    ) {
        static Optional<Element> parse(String line) {
            // Format: [uid] role "name" - details
            // Example: [a1b2] button "Submit"
            try {
                int uidEnd = line.indexOf(']');
                if (uidEnd < 0) return Optional.empty();

                String uid = line.substring(1, uidEnd).trim();
                String rest = line.substring(uidEnd + 1).trim();

                // Find role (first word)
                int spaceIdx = rest.indexOf(' ');
                if (spaceIdx < 0) {
                    return Optional.of(new Element(uid, rest, "", ""));
                }

                String role = rest.substring(0, spaceIdx);
                rest = rest.substring(spaceIdx + 1).trim();

                // Find name in quotes
                String name = "";
                String details = rest;
                if (rest.startsWith("\"")) {
                    int endQuote = rest.indexOf('"', 1);
                    if (endQuote > 0) {
                        name = rest.substring(1, endQuote);
                        details = rest.substring(endQuote + 1).trim();
                    }
                }

                return Optional.of(new Element(uid, role, name, details));
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }
}
