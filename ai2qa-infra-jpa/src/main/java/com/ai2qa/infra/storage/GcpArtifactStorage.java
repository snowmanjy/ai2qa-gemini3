package com.ai2qa.infra.storage;

import com.ai2qa.application.port.ArtifactStorage;
import com.google.cloud.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

/**
 * Google Cloud Storage implementation of ArtifactStorage.
 * Stores screenshots, reports, and other artifacts in GCS buckets.
 *
 * NOTE: This is disabled by default for hackathon. LocalFileArtifactStorage is used instead.
 * To enable GCS storage, set: ai2qa.storage.type=gcs
 */
@Service
@ConditionalOnProperty(name = "ai2qa.storage.type", havingValue = "gcs")
public class GcpArtifactStorage implements ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(GcpArtifactStorage.class);
    private static final int MAX_RETRIES = 3;

    private final Storage storage;
    private final String bucketName;

    public GcpArtifactStorage(
            @Value("${gcp.storage.bucket-name:ai2qa-mvp-artifacts}") String bucketName) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName = bucketName;
        log.info("GCP Artifact Storage initialized with bucket: {}", bucketName);
    }

    @Override
    public void saveScreenshot(String testRunId, int stepIndex, byte[] imageBytes) {
        String blobPath = buildScreenshotPath(testRunId, stepIndex);

        BlobId blobId = BlobId.of(bucketName, blobPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("image/png")
                .build();

        uploadWithRetry(blobInfo, imageBytes, blobPath, "screenshot", MAX_RETRIES);
    }

    @Override
    public Optional<byte[]> loadScreenshot(String testRunId, int stepIndex) {
        String blobPath = buildScreenshotPath(testRunId, stepIndex);

        try {
            Blob blob = storage.get(BlobId.of(bucketName, blobPath));
            if (blob == null || !blob.exists()) {
                log.debug("Screenshot not found: {}/{}", bucketName, blobPath);
                return Optional.empty();
            }
            return Optional.of(blob.getContent());
        } catch (StorageException e) {
            log.error("Failed to load screenshot from GCS: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<InputStream> loadScreenshotAsStream(String testRunId, int stepIndex) {
        return loadScreenshot(testRunId, stepIndex)
                .map(ByteArrayInputStream::new)
                .map(InputStream.class::cast);
    }

    @Override
    public boolean screenshotExists(String testRunId, int stepIndex) {
        String blobPath = buildScreenshotPath(testRunId, stepIndex);

        try {
            Blob blob = storage.get(BlobId.of(bucketName, blobPath));
            return blob != null && blob.exists();
        } catch (StorageException e) {
            log.error("Failed to check screenshot existence: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void saveReport(String testRunId, String filename, byte[] content) {
        String blobPath = buildReportPath(testRunId, filename);
        String contentType = filename.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";

        BlobId blobId = BlobId.of(bucketName, blobPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();

        uploadWithRetry(blobInfo, content, blobPath, "report", MAX_RETRIES);
    }

    /**
     * Uploads content to GCS with retry logic for transient failures.
     * Cloud Run's serverless environment can have stale connections that cause
     * "Error writing request body to server" or "Broken pipe" errors.
     */
    private void uploadWithRetry(BlobInfo blobInfo, byte[] content, String blobPath, String artifactType, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                storage.create(blobInfo, content);
                log.info("{} saved: {}/{} (attempt {})", capitalize(artifactType), bucketName, blobPath, attempt);
                return;
            } catch (StorageException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "unknown error";

                // Check if it's a retryable error (network issues, connection resets)
                boolean isRetryable = isRetryableError(errorMsg);

                if (isRetryable && attempt < maxRetries) {
                    log.warn("GCS upload attempt {} failed for {} (retrying): {}",
                            attempt, blobPath, errorMsg);
                    sleepBeforeRetry(attempt);
                } else if (!isRetryable) {
                    // Non-retryable error (permissions, bucket not found, etc.)
                    log.error("Failed to save {} to GCS (non-retryable): {}", artifactType, errorMsg);
                    throw new RuntimeException("Failed to save " + artifactType, e);
                }
            }
        }

        // All retries exhausted
        log.error("Failed to save {} to GCS after {} attempts: {}",
                artifactType, maxRetries, lastException != null ? lastException.getMessage() : "unknown error");
        throw new RuntimeException("Failed to save " + artifactType + " after " + maxRetries + " attempts", lastException);
    }

    /**
     * Determines if a GCS error is retryable (transient network issues).
     */
    private boolean isRetryableError(String errorMsg) {
        String lowerMsg = errorMsg.toLowerCase();
        return lowerMsg.contains("error writing request body") ||
                lowerMsg.contains("broken pipe") ||
                lowerMsg.contains("connection reset") ||
                lowerMsg.contains("socket closed") ||
                lowerMsg.contains("connection refused") ||
                lowerMsg.contains("timeout") ||
                lowerMsg.contains("503") ||  // Service unavailable
                lowerMsg.contains("500");    // Internal server error
    }

    /**
     * Exponential backoff before retry.
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            // Exponential backoff: 1s, 2s, 4s
            long sleepMs = 1000L * (1L << (attempt - 1));
            Thread.sleep(Math.min(sleepMs, 5000));  // Cap at 5 seconds
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    @Override
    public Optional<byte[]> loadReport(String testRunId, String filename) {
        String blobPath = buildReportPath(testRunId, filename);

        try {
            Blob blob = storage.get(BlobId.of(bucketName, blobPath));
            if (blob == null || !blob.exists()) {
                log.debug("Report not found: {}/{}", bucketName, blobPath);
                return Optional.empty();
            }
            return Optional.of(blob.getContent());
        } catch (StorageException e) {
            log.error("Failed to load report from GCS: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean reportExists(String testRunId, String filename) {
        String blobPath = buildReportPath(testRunId, filename);

        try {
            Blob blob = storage.get(BlobId.of(bucketName, blobPath));
            return blob != null && blob.exists();
        } catch (StorageException e) {
            log.error("Failed to check report existence: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void deleteArtifacts(String testRunId) {
        // Delete screenshots
        String screenshotPrefix = "artifacts/" + testRunId + "/";
        deleteByPrefix(screenshotPrefix);

        // Delete reports
        String reportPrefix = "reports/" + testRunId + "/";
        deleteByPrefix(reportPrefix);

        log.info("Deleted all artifacts for test run: {}", testRunId);
    }

    private void deleteByPrefix(String prefix) {
        try {
            Iterable<Blob> blobs = storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll();
            for (Blob blob : blobs) {
                blob.delete();
                log.debug("Deleted: {}", blob.getName());
            }
        } catch (StorageException e) {
            log.error("Failed to delete artifacts with prefix {}: {}", prefix, e.getMessage());
        }
    }

    private String buildScreenshotPath(String testRunId, int stepIndex) {
        return String.format("artifacts/%s/%d.png", testRunId, stepIndex);
    }

    private String buildReportPath(String testRunId, String filename) {
        return String.format("reports/%s/%s", testRunId, filename);
    }
}
