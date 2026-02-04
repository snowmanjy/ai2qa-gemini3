package com.ai2qa.domain.model;

import java.time.Instant;
import java.util.Optional;

/**
 * Represents a DOM accessibility tree snapshot at a point in time.
 *
 * <p>Used for comparing before/after states and for AI-based element finding.
 *
 * @param content    The accessibility tree text representation
 * @param url        The page URL when snapshot was taken
 * @param title      The page title when snapshot was taken
 * @param capturedAt When the snapshot was captured
 */
public record DomSnapshot(
        String content,
        String url,
        String title,
        Instant capturedAt
) {

    /**
     * Creates a snapshot with current timestamp.
     */
    public static DomSnapshot of(String content, String url, String title) {
        return new DomSnapshot(content, url, title, Instant.now());
    }

    /**
     * Creates an empty snapshot (e.g., before page load).
     */
    public static DomSnapshot empty() {
        return new DomSnapshot("", "", "", Instant.now());
    }

    /**
     * Checks if this snapshot has content.
     */
    public boolean hasContent() {
        return content != null && !content.isBlank();
    }

    /**
     * Returns content if present.
     */
    public Optional<String> contentOpt() {
        return Optional.ofNullable(content).filter(s -> !s.isBlank());
    }

    /**
     * Returns approximate line count for the snapshot.
     */
    public int lineCount() {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.split("\n").length;
    }

    /**
     * Extracts a portion of the snapshot around a keyword.
     *
     * @param keyword     The keyword to search for
     * @param contextLines Number of lines before/after to include
     * @return Extracted context or empty if keyword not found
     */
    public Optional<String> extractContext(String keyword, int contextLines) {
        if (content == null || keyword == null) {
            return Optional.empty();
        }

        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(keyword.toLowerCase())) {
                int start = Math.max(0, i - contextLines);
                int end = Math.min(lines.length, i + contextLines + 1);

                StringBuilder context = new StringBuilder();
                for (int j = start; j < end; j++) {
                    context.append(lines[j]).append("\n");
                }
                return Optional.of(context.toString().trim());
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the snapshot contains a specific text.
     */
    public boolean containsText(String text) {
        return content != null && content.toLowerCase().contains(text.toLowerCase());
    }
}
