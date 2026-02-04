package com.ai2qa.mcp.tools;

import com.ai2qa.mcp.McpClient;

import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tool for capturing screenshots of pages or elements.
 */
public class ScreenshotTool {

    private static final String TOOL_NAME = "take_screenshot";

    private final McpClient mcpClient;

    public ScreenshotTool(McpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    /**
     * Takes a screenshot of the visible viewport.
     *
     * @return Screenshot result with base64 image data
     */
    public Result takeScreenshot() {
        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, Map.of());
        return Result.from(response);
    }

    /**
     * Takes a full page screenshot.
     *
     * @return Screenshot result with base64 image data
     */
    public Result takeFullPageScreenshot() {
        Map<String, Object> response = mcpClient.callTool(
                TOOL_NAME,
                Map.of("fullPage", true)
        );
        return Result.from(response);
    }

    /**
     * Takes a screenshot of a specific element.
     *
     * @param uid The element UID from the page snapshot
     * @return Screenshot result with base64 image data
     */
    public Result takeElementScreenshot(String uid) {
        Map<String, Object> response = mcpClient.callTool(
                TOOL_NAME,
                Map.of("uid", uid)
        );
        return Result.from(response);
    }

    /**
     * Takes a screenshot and saves it to a file.
     *
     * @param filePath Path to save the screenshot
     * @return Screenshot result
     */
    public Result takeScreenshotToFile(Path filePath) {
        Map<String, Object> response = mcpClient.callTool(
                TOOL_NAME,
                Map.of("filePath", filePath.toString())
        );
        return Result.from(response);
    }

    /**
     * Takes a screenshot with custom options.
     *
     * @param options Screenshot options
     * @return Screenshot result
     */
    public Result takeScreenshot(Options options) {
        Map<String, Object> params = new HashMap<>();

        if (options.fullPage()) {
            params.put("fullPage", true);
        }
        if (options.uid() != null) {
            params.put("uid", options.uid());
        }
        if (options.filePath() != null) {
            params.put("filePath", options.filePath());
        }
        if (options.format() != null) {
            params.put("format", options.format().name().toLowerCase());
        }
        if (options.quality() != null) {
            params.put("quality", options.quality());
        }

        Map<String, Object> response = mcpClient.callTool(TOOL_NAME, params);
        return Result.from(response);
    }

    /**
     * Screenshot options.
     */
    public record Options(
            boolean fullPage,
            String uid,
            String filePath,
            Format format,
            Integer quality
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean fullPage = false;
            private String uid = null;
            private String filePath = null;
            private Format format = null;
            private Integer quality = null;

            public Builder fullPage(boolean fullPage) {
                this.fullPage = fullPage;
                return this;
            }

            public Builder elementUid(String uid) {
                this.uid = uid;
                return this;
            }

            public Builder saveTo(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public Builder saveTo(Path filePath) {
                this.filePath = filePath.toString();
                return this;
            }

            public Builder format(Format format) {
                this.format = format;
                return this;
            }

            public Builder quality(int quality) {
                this.quality = quality;
                return this;
            }

            public Options build() {
                return new Options(fullPage, uid, filePath, format, quality);
            }
        }
    }

    /**
     * Screenshot format.
     */
    public enum Format {
        PNG,
        JPEG,
        WEBP
    }

    /**
     * Screenshot result.
     */
    public record Result(
            boolean success,
            Optional<byte[]> imageData,
            String mimeType,
            Optional<String> filePath,
            Optional<String> error
    ) {
        @SuppressWarnings("unchecked")
        static Result from(Map<String, Object> response) {
            var content = (List<Map<String, Object>>) response.get("content");

            if (content != null && !content.isEmpty()) {
                var firstContent = content.get(0);
                String type = (String) firstContent.get("type");

                if ("image".equals(type)) {
                    String data = (String) firstContent.get("data");
                    String mimeType = (String) firstContent.getOrDefault("mimeType", "image/png");

                    byte[] imageBytes = null;
                    if (data != null) {
                        imageBytes = Base64.getDecoder().decode(data);
                    }

                    return new Result(
                            true,
                            Optional.ofNullable(imageBytes),
                            mimeType,
                            Optional.empty(),
                            Optional.empty()
                    );
                } else if ("text".equals(type)) {
                    // File was saved to disk
                    String text = (String) firstContent.get("text");
                    return new Result(
                            true,
                            Optional.empty(),
                            "image/png",
                            Optional.of(text),
                            Optional.empty()
                    );
                }
            }

            boolean hasError = response.containsKey("error");
            return new Result(
                    !hasError,
                    Optional.empty(),
                    "image/png",
                    Optional.empty(),
                    hasError ? Optional.of(String.valueOf(response.get("error"))) : Optional.empty()
            );
        }

        /**
         * Gets the image data as a base64 string.
         */
        public Optional<String> base64Image() {
            return imageData.map(Base64.getEncoder()::encodeToString);
        }
    }
}
