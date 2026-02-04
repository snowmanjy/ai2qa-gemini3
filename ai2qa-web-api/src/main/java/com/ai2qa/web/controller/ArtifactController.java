package com.ai2qa.web.controller;

import com.ai2qa.application.port.ArtifactStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for serving test run artifacts (screenshots, videos).
 * Acts as a secure proxy to cloud storage - no public buckets needed.
 */
@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactController {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class);

    private final ArtifactStorage artifactStorage;

    public ArtifactController(ArtifactStorage artifactStorage) {
        this.artifactStorage = artifactStorage;
    }

    /**
     * Serves a screenshot for a specific test run step.
     * Authenticated users only.
     *
     * @param testRunId The test run ID
     * @param stepIndex The step index (0-based)
     * @return PNG image bytes
     */
    @GetMapping("/{testRunId}/{stepIndex}")
    public ResponseEntity<byte[]> getScreenshot(
            @PathVariable String testRunId,
            @PathVariable int stepIndex) {

        log.debug("Loading screenshot: testRunId={}, stepIndex={}", testRunId, stepIndex);

        var imageBytes = artifactStorage.loadScreenshot(testRunId, stepIndex);
        if (imageBytes.isEmpty()) {
            log.debug("Screenshot not found: testRunId={}, stepIndex={}", testRunId, stepIndex);
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(imageBytes.get().length);
        headers.setCacheControl("public, max-age=31536000"); // Cache for 1 year (immutable)

        return new ResponseEntity<>(imageBytes.get(), headers, HttpStatus.OK);
    }

    /**
     * Checks if a screenshot exists for a step.
     *
     * @param testRunId The test run ID
     * @param stepIndex The step index
     * @return 200 if exists, 404 if not
     */
    @GetMapping("/{testRunId}/{stepIndex}/exists")
    public ResponseEntity<Void> screenshotExists(
            @PathVariable String testRunId,
            @PathVariable int stepIndex) {

        boolean exists = artifactStorage.screenshotExists(testRunId, stepIndex);
        return exists ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
