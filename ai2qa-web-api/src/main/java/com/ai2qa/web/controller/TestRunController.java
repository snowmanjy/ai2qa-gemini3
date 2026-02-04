package com.ai2qa.web.controller;

import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.application.run.TestRunService;
import com.ai2qa.domain.context.TenantContext;
import com.ai2qa.application.run.view.ExecutedStepView;
import com.ai2qa.application.run.view.PagedResultView;
import com.ai2qa.application.run.view.TestRunView;
import com.ai2qa.web.dto.CreateTestRunRequest;
import com.ai2qa.web.dto.TestRunResponse;
import com.ai2qa.web.exception.EntityNotFoundException;
import com.ai2qa.web.service.DailyCapService;
import com.ai2qa.web.service.RecaptchaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/test-runs")
public class TestRunController {

    private static final Logger log = LoggerFactory.getLogger(TestRunController.class);

    private final TestRunService testRunService;
    private final ArtifactStorage artifactStorage;
    private final RecaptchaService recaptchaService;
    private final DailyCapService dailyCapService;

    public TestRunController(
            TestRunService testRunService,
            ArtifactStorage artifactStorage,
            RecaptchaService recaptchaService,
            DailyCapService dailyCapService) {
        this.testRunService = testRunService;
        this.artifactStorage = artifactStorage;
        this.recaptchaService = recaptchaService;
        this.dailyCapService = dailyCapService;
    }

    /**
     * Lists test runs for the current tenant with pagination.
     *
     * @param page Page number (0-indexed)
     * @param size Page size (default 20, max 100)
     * @return Paged result of test runs
     */
    @GetMapping
    public ResponseEntity<PagedResultView<TestRunResponse>> listTestRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String tenantId = TenantContext.getTenantId();
        PagedResultView<TestRunView> testRuns = testRunService.listTestRuns(tenantId, page, size);
        PagedResultView<TestRunResponse> response = testRuns.map(TestRunResponse::from);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createTestRun(
            @Valid @RequestBody CreateTestRunRequest request) {

        // Layer 1: reCAPTCHA verification (if enabled)
        if (recaptchaService.isEnabled() && !recaptchaService.verify(request.recaptchaToken())) {
            log.warn("reCAPTCHA verification failed for request to {}", request.targetUrl());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "recaptcha_failed",
                            "message", "reCAPTCHA verification failed. Please try again."
                    ));
        }

        // Layer 2: Daily cap enforcement
        if (!dailyCapService.canExecute()) {
            log.warn("Daily cap reached. Remaining: 0/{}", dailyCapService.getDailyCap());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                            "error", "demo_limit_reached",
                            "message", "Daily demo limit reached. Please try again tomorrow!",
                            "remaining", 0,
                            "dailyCap", dailyCapService.getDailyCap()
                    ));
        }

        String tenantId = TenantContext.getTenantId();

        log.info("Creating test run: targetUrl={}, persona={}",
                request.targetUrl(), request.persona());

        // Build effective goals: if additionalContext provided, include it as context for the AI
        List<String> effectiveGoals = new ArrayList<>(request.goals() != null ? request.goals() : List.of());
        if (request.additionalContext() != null && !request.additionalContext().isBlank()) {
            effectiveGoals.add(0, "[User Context]: " + request.additionalContext().trim());
        }

        TestRunView testRun = testRunService.createTestRun(
                tenantId,
                request.targetUrl(),
                effectiveGoals,
                request.persona(),
                request.cookiesJson(),
                false,  // No email notifications
                null,   // No verified email
                "CLOUD", // Always cloud mode
                null);   // No agent ID

        // Increment daily count after successful creation
        dailyCapService.increment();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(TestRunResponse.from(testRun));
    }

    /**
     * Get remaining daily test runs.
     */
    @GetMapping("/remaining")
    public ResponseEntity<Map<String, Object>> getRemainingRuns() {
        return ResponseEntity.ok(Map.of(
                "remaining", dailyCapService.remaining(),
                "dailyCap", dailyCapService.getDailyCap()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TestRunResponse> getTestRun(@PathVariable String id) {
        TestRunView testRun = testRunService.getTestRun(id)
                .orElseThrow(() -> EntityNotFoundException.testRun(id));

        return ResponseEntity.ok(TestRunResponse.from(testRun));
    }

    @GetMapping("/{id}/log")
    public ResponseEntity<java.util.List<ExecutedStepView>> getTestRunLog(@PathVariable String id) {
        return testRunService.getTestRunLog(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> EntityNotFoundException.testRun(id));
    }

    /**
     * Get screenshot for a test run step.
     */
    @GetMapping("/{id}/screenshots/{stepIndex}")
    public ResponseEntity<byte[]> getScreenshot(
            @PathVariable String id,
            @PathVariable int stepIndex) {
        return artifactStorage.loadScreenshot(id, stepIndex)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                        .body(bytes))
                .orElse(ResponseEntity.notFound().build());
    }
}
