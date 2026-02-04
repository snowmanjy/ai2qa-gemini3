package com.ai2qa.application.port;

import java.io.InputStream;
import java.util.Optional;

/**
 * Cloud-agnostic artifact storage interface.
 * Abstracts storage of screenshots, reports, and other test artifacts.
 */
public interface ArtifactStorage {

    /**
     * Saves a screenshot for a specific test run step.
     *
     * @param testRunId  The test run identifier
     * @param stepIndex  The step index (0-based)
     * @param imageBytes The PNG image bytes
     */
    void saveScreenshot(String testRunId, int stepIndex, byte[] imageBytes);

    /**
     * Loads a screenshot for a specific test run step.
     *
     * @param testRunId The test run identifier
     * @param stepIndex The step index (0-based)
     * @return Optional image bytes if found
     */
    Optional<byte[]> loadScreenshot(String testRunId, int stepIndex);

    /**
     * Loads a screenshot as an InputStream (for streaming large files).
     *
     * @param testRunId The test run identifier
     * @param stepIndex The step index (0-based)
     * @return Optional InputStream of the image if found
     */
    Optional<InputStream> loadScreenshotAsStream(String testRunId, int stepIndex);

    /**
     * Checks if a screenshot exists for a specific step.
     *
     * @param testRunId The test run identifier
     * @param stepIndex The step index (0-based)
     * @return true if screenshot exists
     */
    boolean screenshotExists(String testRunId, int stepIndex);

    /**
     * Saves a report file for a test run.
     *
     * @param testRunId The test run identifier
     * @param filename  The filename (e.g., "report.pdf")
     * @param content   The file content
     */
    void saveReport(String testRunId, String filename, byte[] content);

    /**
     * Loads a report file for a test run.
     *
     * @param testRunId The test run identifier
     * @param filename  The filename (e.g., "report.pdf")
     * @return Optional file content if found
     */
    Optional<byte[]> loadReport(String testRunId, String filename);

    /**
     * Checks if a report exists for a test run.
     *
     * @param testRunId The test run identifier
     * @param filename  The filename (e.g., "report.pdf")
     * @return true if report exists
     */
    boolean reportExists(String testRunId, String filename);

    /**
     * Deletes all artifacts for a test run.
     *
     * @param testRunId The test run identifier
     */
    void deleteArtifacts(String testRunId);
}
