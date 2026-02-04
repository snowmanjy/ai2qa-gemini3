package com.ai2qa.infra.storage;

import com.ai2qa.application.port.ArtifactStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * Local filesystem implementation of ArtifactStorage.
 * Stores screenshots and reports in /tmp/ai2qa/artifacts directory.
 *
 * Perfect for hackathon demos:
 * - Zero config (no GCS bucket, service account, or IAM)
 * - Works on Cloud Run (uses ephemeral disk)
 * - Artifacts survive the session (container stays warm while judge is using it)
 */
@Service
@Primary  // Override GcpArtifactStorage as the default implementation
public class LocalFileArtifactStorage implements ArtifactStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileArtifactStorage.class);
    private static final Path BASE_DIR = Path.of("/tmp/ai2qa/artifacts");

    public LocalFileArtifactStorage() {
        try {
            Files.createDirectories(BASE_DIR);
            log.info("Local artifact storage initialized at: {}", BASE_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create artifact directory: " + BASE_DIR, e);
        }
    }

    @Override
    public void saveScreenshot(String testRunId, int stepIndex, byte[] imageBytes) {
        Path dir = BASE_DIR.resolve("screenshots").resolve(testRunId);
        Path path = dir.resolve(stepIndex + ".png");
        try {
            Files.createDirectories(dir);
            Files.write(path, imageBytes);
            log.debug("Screenshot saved: {}", path);
        } catch (IOException e) {
            log.error("Failed to save screenshot to {}: {}", path, e.getMessage());
            throw new RuntimeException("Failed to save screenshot", e);
        }
    }

    @Override
    public Optional<byte[]> loadScreenshot(String testRunId, int stepIndex) {
        Path path = BASE_DIR.resolve("screenshots").resolve(testRunId).resolve(stepIndex + ".png");
        return readFile(path);
    }

    @Override
    public Optional<InputStream> loadScreenshotAsStream(String testRunId, int stepIndex) {
        return loadScreenshot(testRunId, stepIndex)
                .map(ByteArrayInputStream::new)
                .map(InputStream.class::cast);
    }

    @Override
    public boolean screenshotExists(String testRunId, int stepIndex) {
        Path path = BASE_DIR.resolve("screenshots").resolve(testRunId).resolve(stepIndex + ".png");
        return Files.exists(path);
    }

    @Override
    public void saveReport(String testRunId, String filename, byte[] content) {
        Path dir = BASE_DIR.resolve("reports").resolve(testRunId);
        Path path = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            Files.write(path, content);
            log.debug("Report saved: {}", path);
        } catch (IOException e) {
            log.error("Failed to save report to {}: {}", path, e.getMessage());
            throw new RuntimeException("Failed to save report", e);
        }
    }

    @Override
    public Optional<byte[]> loadReport(String testRunId, String filename) {
        Path path = BASE_DIR.resolve("reports").resolve(testRunId).resolve(filename);
        return readFile(path);
    }

    @Override
    public boolean reportExists(String testRunId, String filename) {
        Path path = BASE_DIR.resolve("reports").resolve(testRunId).resolve(filename);
        return Files.exists(path);
    }

    @Override
    public void deleteArtifacts(String testRunId) {
        deleteDirectory(BASE_DIR.resolve("screenshots").resolve(testRunId));
        deleteDirectory(BASE_DIR.resolve("reports").resolve(testRunId));
        log.info("Deleted all artifacts for test run: {}", testRunId);
    }

    private Optional<byte[]> readFile(Path path) {
        try {
            if (Files.exists(path)) {
                return Optional.of(Files.readAllBytes(path));
            }
            log.debug("File not found: {}", path);
            return Optional.empty();
        } catch (IOException e) {
            log.error("Failed to read file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private void deleteDirectory(Path dir) {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete {}: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.error("Failed to delete directory {}: {}", dir, e.getMessage());
            }
        }
    }
}
