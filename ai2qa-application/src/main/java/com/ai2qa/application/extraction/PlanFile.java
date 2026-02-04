package com.ai2qa.application.extraction;

/**
 * Immutable file payload for plan extraction.
 *
 * @param filename     Original filename
 * @param contentType  MIME type (may be blank)
 * @param contentBytes File content
 */
public record PlanFile(
        String filename,
        String contentType,
        byte[] contentBytes
) {
    public PlanFile {
        if (contentBytes == null) {
            throw new IllegalArgumentException("File content is required");
        }
        if (filename == null) {
            filename = "";
        }
        if (contentType == null) {
            contentType = "";
        }
    }

    public long size() {
        return contentBytes.length;
    }
}
