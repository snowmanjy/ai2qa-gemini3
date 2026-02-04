package com.ai2qa.application.orchestrator;

import com.ai2qa.application.cache.SmartDriver;
import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.application.port.OrchestratorConfigProvider;
import com.ai2qa.application.planner.StepPlanner;
import com.ai2qa.application.security.PlanSanitizer;
import com.ai2qa.application.security.PromptInjectionDetector;
import com.ai2qa.application.util.Sleeper;
import com.ai2qa.domain.model.ExecutionMode;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.PerformanceMetrics;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.port.ActionQueuePort;
import com.ai2qa.domain.port.DoneQueuePort;
import com.ai2qa.domain.repository.TestRunRepository;
import com.ai2qa.domain.result.Result;
import com.ai2qa.application.port.BrowserDriverPort;
import com.ai2qa.application.event.TestRunCompletedSpringEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrator that manages the agent lifecycle (planning, execution,
 * reflection) for a test run.
 * Implements the "Clean Room" pattern with try-finally resource cleanup.
 */
@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Fallback CSS selectors to try when AI obstacle detection fails.
     * These target common consent/cookie banner buttons that the AI might miss,
     * especially when consent dialogs are in iframes not captured in the snapshot.
     * Inspired by the "chaos monster" persona accidentally clicking consent buttons.
     */
    private static final List<String> FALLBACK_CONSENT_SELECTORS = List.of(
            // OneTrust (CNN, many news sites)
            "#onetrust-accept-btn-handler",
            "button[id*='onetrust-accept']",
            // SourcePoint (CNN)
            "button[title='Accept']",
            "button[title='Agree']",
            ".sp_choice_type_11",  // SourcePoint accept button class
            // Generic accept/agree buttons
            "button[id*='accept']",
            "button[id*='agree']",
            "button[class*='accept']",
            "button[class*='agree']",
            "button[class*='consent']",
            // Data attributes
            "[data-testid*='accept']",
            "[data-testid*='agree']",
            "[data-cy*='accept']",
            // ARIA labels
            "button[aria-label*='Accept']",
            "button[aria-label*='Agree']",
            // Text-based (less reliable but catches edge cases)
            "button:contains('Accept All')",
            "button:contains('I Accept')",
            "button:contains('I Agree')",
            "button:contains('Agree')",
            "button:contains('Accept')"
    );

    private final StepPlanner stepPlanner;
    private final BrowserDriverPort browserDriver;
    private final ActionQueuePort actionQueue;
    private final DoneQueuePort doneQueue;
    private final TestRunRepository testRunRepository;
    private final SmartDriver smartDriver;
    private final ArtifactStorage artifactStorage;
    private final PromptInjectionDetector promptInjectionDetector;
    private final PlanSanitizer planSanitizer;
    private final Reflector reflector;
    private final ObstacleDetector obstacleDetector;
    private final OptimizationSuggestionService suggestionService;
    private final OrchestratorConfigProvider config;
    private final Clock clock;
    private final Sleeper sleeper;
    private final ApplicationEventPublisher eventPublisher;

    public AgentOrchestrator(
            StepPlanner stepPlanner,
            BrowserDriverPort browserDriver,
            ActionQueuePort actionQueue,
            DoneQueuePort doneQueue,
            TestRunRepository testRunRepository,
            SmartDriver smartDriver,
            ArtifactStorage artifactStorage,
            PromptInjectionDetector promptInjectionDetector,
            PlanSanitizer planSanitizer,
            Reflector reflector,
            ObstacleDetector obstacleDetector,
            OptimizationSuggestionService suggestionService,
            OrchestratorConfigProvider config,
            Clock clock,
            Sleeper sleeper,
            ApplicationEventPublisher eventPublisher) {
        this.stepPlanner = stepPlanner;
        this.browserDriver = browserDriver;
        this.actionQueue = actionQueue;
        this.doneQueue = doneQueue;
        this.testRunRepository = testRunRepository;
        this.smartDriver = smartDriver;
        this.artifactStorage = artifactStorage;
        this.promptInjectionDetector = promptInjectionDetector;
        this.planSanitizer = planSanitizer;
        this.reflector = reflector;
        this.obstacleDetector = obstacleDetector;
        this.suggestionService = suggestionService;
        this.config = config;
        this.clock = clock;
        this.sleeper = sleeper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Gets the browser driver for execution.
     * Hackathon: Always uses cloud browser (local agent removed).
     */
    private BrowserDriverPort getBrowserDriver() {
        return browserDriver;
    }

    /**
     * Executes a test run.
     *
     * @param testRun The test run to execute
     */
    public void execute(TestRun testRun) {
        log.info("Starting test run: {}", testRun.getId());

        String tenantId = testRun.getTenantId();

        // 1. Pre-Flight Security Check - Fail fast, no cleanup needed yet
        try {
            if (!promptInjectionDetector.areSafe(testRun.getGoals())) {
                failRun(testRun, "Security check failed: Prompt injection detected.");
                return;
            }
        } catch (Exception e) {
            log.error("Pre-flight check failed", e);
            failRun(testRun, "SYSTEM_ERROR: Exception during pre-checks: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return;
        }

        // Hackathon: Always use cloud browser (local agent removed)
        log.info("============================================================");
        log.info("[EXECUTION MODE] CLOUD - using cloud Playwright browser");
        log.info("============================================================");

        // CRITICAL: Start overall timeout clock BEFORE any potentially blocking operations
        // This protects against hangs in planning, context creation, or execution
        java.time.Instant overallStartTime = clock.instant();
        java.time.Duration maxDuration = java.time.Duration.ofMinutes(config.getTestTimeoutMinutes());
        log.info("Test run started at {} with overall timeout of {} minutes", overallStartTime, config.getTestTimeoutMinutes());

        // 2. The Execution Phase (Requires Cleanup)
        try {
            // Ensure browser is ready with retry logic for race conditions
            // There's a TOCTOU race between isRunning() and createContext() - the MCP
            // process can die between the check and the call, especially under thread starvation
            ensureBrowserReady(browserDriver, testRun.getId().value().toString());

            // Check timeout after context creation (can be slow on cold starts)
            checkOverallTimeout(overallStartTime, maxDuration, testRun, "context creation");

            // B. Planning
            log.info("Generating plan for goals: {} with {} persona", testRun.getGoals(), testRun.getPersona());
            List<ActionStep> rawPlan = stepPlanner.createPlan(
                    testRun.getTargetUrl(),
                    testRun.getGoals(),
                    testRun.getPersona());

            // Check timeout after planning (AI calls can be slow)
            checkOverallTimeout(overallStartTime, maxDuration, testRun, "planning");

            // Sanitize plan: remove malformed steps (empty URLs, oversized inputs)
            List<ActionStep> plan = planSanitizer.sanitize(rawPlan, testRun.getTargetUrl());

            if (plan.isEmpty()) {
                failRun(testRun, "Plan generation failed: No valid steps after sanitization.");
                return;
            }

            // Security check: verify no unauthorized domain navigations
            if (!planSanitizer.isSafe(plan, testRun.getTargetUrl())) {
                failRun(testRun, "Security check failed: Unsafe actions in plan.");
                return;
            }

            // C. Start Run
            testRun.start(plan, clock.instant());
            testRunRepository.save(testRun);
            actionQueue.pushAll(testRun.getId(), plan);

            // D. Execution Loop (pass the start time so it uses the same clock)
            executeLoop(testRun, overallStartTime, maxDuration);

        } catch (TimeoutException e) {
            // Timeout already handled and logged in checkOverallTimeout
            log.warn("Test run timed out: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Test run crashed", e);
            failRun(testRun, "SYSTEM_ERROR: Internal Engine Error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        } finally {
            // 4. THE CLEANUP (The "Housekeeping")
            // This runs guaranteed, even if the engine crashes.
            cleanup(testRun.getId().value().toString());
        }
    }

    /**
     * Checks if the overall test timeout has been exceeded.
     * Throws TimeoutException if exceeded.
     *
     * @param startTime   When the test started
     * @param maxDuration Maximum allowed duration
     * @param testRun     The test run (for failing if timeout)
     * @param phase       Current phase (for logging)
     */
    private void checkOverallTimeout(java.time.Instant startTime, java.time.Duration maxDuration,
                                      TestRun testRun, String phase) throws TimeoutException {
        java.time.Duration elapsed = java.time.Duration.between(startTime, clock.instant());
        if (elapsed.compareTo(maxDuration) > 0) {
            String message = String.format(
                    "TIMEOUT: Test exceeded maximum duration of %d minutes during %s phase (elapsed: %d min %d sec)",
                    config.getTestTimeoutMinutes(),
                    phase,
                    elapsed.toMinutes(),
                    elapsed.minusMinutes(elapsed.toMinutes()).getSeconds());
            log.error("Test run timed out during {}: {} minutes elapsed", phase, elapsed.toMinutes());
            failRun(testRun, message);
            throw new TimeoutException(message);
        }
    }

    /**
     * Custom TimeoutException for test execution timeouts.
     */
    private static class TimeoutException extends Exception {
        TimeoutException(String message) {
            super(message);
        }
    }

    /**
     * Ensures browser is ready and context is created, with retry for race conditions.
     *
     * <p>There's a TOCTOU race between isRunning() and createContext() - the MCP
     * process can die between the check and the call, especially under thread starvation.
     * This method handles that by retrying with a fresh start if context creation fails.
     *
     * @param browserDriver The browser driver to use
     * @param runId The test run ID for context tracking
     */
    private void ensureBrowserReady(BrowserDriverPort browserDriver, String runId) {
        int maxRetries = 2;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                // Always check and start if needed
                if (!browserDriver.isRunning()) {
                    log.info("Browser not running, starting...");
                    browserDriver.start();
                }

                // Create context - this is where the race condition can manifest
                browserDriver.createContext(false, runId);
                log.info("Browser context created successfully (attempt {})", attempt + 1);
                return; // Success!

            } catch (Exception e) {
                lastException = e;
                log.warn("Browser context creation failed (attempt {}): {}", attempt + 1, e.getMessage());

                if (attempt < maxRetries) {
                    // Force restart the browser and retry
                    log.info("Forcing browser restart before retry...");
                    try {
                        browserDriver.forceRestart();
                        sleeper.sleep(500); // Brief pause after restart
                    } catch (Exception restartEx) {
                        log.error("Failed to force restart browser: {}", restartEx.getMessage());
                    }
                }
            }
        }

        // All retries exhausted
        throw new RuntimeException("Failed to create browser context after " + (maxRetries + 1) +
                " attempts: " + (lastException != null ? lastException.getMessage() : "unknown error"), lastException);
    }

    private void executeLoop(TestRun testRun, java.time.Instant testStartTime, java.time.Duration maxDuration) {
        Map<String, Integer> retryCounts = new HashMap<>();
        // Track dismissed obstacles across the ENTIRE test run to avoid infinite loops
        java.util.Set<String> dismissedObstacleTypes = new java.util.HashSet<>();
        int loopIterations = 0;

        log.info("Execution loop starting, elapsed so far: {} seconds",
                java.time.Duration.between(testStartTime, clock.instant()).getSeconds());

        // Use getStatus().isActive() logic manually or check status enum
        while (testRun.getStatus() != null && testRun.getStatus().ordinal() < TestRunStatus.COMPLETED.ordinal()) {
            // Global safety check 1: Prevent runaway iteration loops
            loopIterations++;
            if (loopIterations > config.getMaxLoopIterations()) {
                log.error("Test run exceeded {} iterations - likely stuck in infinite loop, terminating",
                        config.getMaxLoopIterations());
                failRun(testRun, "SYSTEM_ERROR: Test exceeded maximum iterations (" + config.getMaxLoopIterations() +
                        ") - terminated to prevent infinite loop");
                break;
            }

            // Global safety check 2: CRITICAL timeout protection
            // Prevents tests from hanging forever due to AI timeouts, Chrome hangs, etc.
            java.time.Duration elapsed = java.time.Duration.between(testStartTime, clock.instant());
            if (elapsed.compareTo(maxDuration) > 0) {
                log.error("Test run exceeded timeout limit: {} minutes (elapsed: {} minutes) - terminating",
                        config.getTestTimeoutMinutes(), elapsed.toMinutes());
                failRun(testRun, String.format(
                        "TIMEOUT: Test exceeded maximum duration of %d minutes (elapsed: %d min %d sec) - " +
                        "terminated to prevent infinite hang. This may indicate AI API slowness, Chrome hangs, " +
                        "or other blocking operations.",
                        config.getTestTimeoutMinutes(),
                        elapsed.toMinutes(),
                        elapsed.minusMinutes(elapsed.toMinutes()).getSeconds()));
                break;
            }

            Optional<ActionStep> nextStepOpt = actionQueue.pop(testRun.getId());

            if (nextStepOpt.isEmpty()) {
                // Check termination conditions
                if (testRun.getStatus() == TestRunStatus.COMPLETED || testRun.getStatus() == TestRunStatus.FAILED) {
                    log.info("Test run completed/terminated");
                    break;
                } else {
                    // Implicit completion if queue is empty
                    testRun.complete(clock.instant());
                    testRunRepository.save(testRun);
                    break;
                }
            }

            ActionStep step = nextStepOpt.get();
            try {
                int retryCount = retryCounts.getOrDefault(step.stepId(), 0);
                executeStep(testRun, step, retryCount, retryCounts, dismissedObstacleTypes);
            } catch (Exception e) {
                log.error("Step execution failed", e);
                // Fail run on unhandled exception - categorize as system error
                failRun(testRun, "SYSTEM_ERROR: Step execution failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                break;
            }
        }

        // Publish completion event for notifications (Slack, etc.)
        eventPublisher.publishEvent(new TestRunCompletedSpringEvent(this, testRun));
    }

    private void executeStep(TestRun testRun, ActionStep step, int retryCount, Map<String, Integer> retryCounts,
                              java.util.Set<String> dismissedObstacleTypes) {
        log.info("Executing step: {}", step);
        Instant startTime = clock.instant();

        // 1. Snapshot Before
        DomSnapshot before = getSnapshot();

        // 2. Proactive Obstacle Detection - "A human QA would just click the Agree button"
        // Before executing any step, check if there's a blocking popup/modal and dismiss it
        // Note: dismissedObstacleTypes tracks obstacles dismissed across the ENTIRE test run
        before = clearObstaclesIfPresent(testRun, before, dismissedObstacleTypes);

        SelectorResolution selectorResolution = resolveSelector(testRun, step, before);
        if (selectorResolution.isUnresolved()) {
            ReflectionResult reflection = reflector.reflect(
                    step,
                    before,
                    null,
                    "Element not found for target: " + Optional.ofNullable(step.target()).orElse(""),
                    retryCount);
            handleReflection(testRun, step, reflection, before, null, 0, retryCount, retryCounts, selectorResolution, List.of(), null);
            return;
        }

        ActionStep resolvedStep = selectorResolution.step();

        // 2. Execute Action
        Map<String, Object> params = buildToolParams(resolvedStep);
        String toolName = mapActionToTool(resolvedStep.action());

        List<String> consoleErrors = List.of();

        try {
            // Auto-scroll before screenshot if target implies a specific page area
            if ("screenshot".equals(resolvedStep.action())) {
                autoScrollForScreenshot(resolvedStep.target());
            }

            Map<String, Object> toolResult = getBrowserDriver().callTool(toolName, params);
            consoleErrors = extractLogs(toolResult);

            // Extract performance metrics if this is a measure_performance step
            PerformanceMetrics performanceMetrics = null;
            if ("measure_performance".equals(resolvedStep.action())) {
                performanceMetrics = extractPerformanceMetrics(toolResult);
            }

            // 3. Snapshot After
            DomSnapshot after = getSnapshot();

            long durationMs = Duration.between(startTime, clock.instant()).toMillis();

            // 4. Reflect
            ReflectionResult reflection = reflector.reflect(resolvedStep, before, after, null, retryCount);

            handleReflection(testRun, resolvedStep, reflection, before, after, durationMs, retryCount,
                    retryCounts, selectorResolution, consoleErrors, performanceMetrics);

        } catch (Exception e) {
            log.warn("Tool execution failed: {}", e.getMessage());
            long durationMs = Duration.between(startTime, clock.instant()).toMillis();
            // Reflection on failure
            ReflectionResult reflection = reflector.reflect(resolvedStep, before, null, e.getMessage(), retryCount);
            handleReflection(testRun, resolvedStep, reflection, before, null, durationMs, retryCount,
                    retryCounts, selectorResolution, consoleErrors, null);
        }
    }

    private void handleReflection(
            TestRun testRun,
            ActionStep step,
            ReflectionResult result,
            DomSnapshot before,
            DomSnapshot after,
            long durationMs,
            int retryCount,
            Map<String, Integer> retryCounts,
            SelectorResolution selectorResolution,
            List<String> consoleErrors,
            PerformanceMetrics performanceMetrics) {

        switch (result) {
            case ReflectionResult.Success s -> {
                // Generate AI optimization suggestion if there are errors to analyze
                String optimizationSuggestion = suggestionService.generateSuggestion(
                        step.action(),
                        step.target(),
                        s.selectorUsed(),
                        consoleErrors,
                        List.of(), // networkErrors not captured yet
                        after,
                        true
                ).orElse(null);

                ExecutedStep executedStep = ExecutedStep.success(
                        step,
                        s.selectorUsed(),
                        before,
                        after,
                        durationMs,
                        retryCount,
                        optimizationSuggestion,
                        null,
                        null,
                        consoleErrors,
                        performanceMetrics,
                        clock.instant());
                int stepIndex = testRun.getExecutedSteps().size();
                saveScreenshot(testRun.getId().value().toString(), stepIndex);
                testRun.recordStepExecution(executedStep, clock.instant());
                testRunRepository.save(testRun);
                doneQueue.record(testRun.getId(), executedStep);
                selectorResolution.recordOutcome(testRun.getTenantId(), smartDriver, true);
                retryCounts.remove(step.stepId());
            }
            case ReflectionResult.Retry r -> {
                log.info("Retrying step: {}", r.reason());
                selectorResolution.recordOutcome(testRun.getTenantId(), smartDriver, false);
                // Simplified for hackathon: just re-add step to end of queue (simple retry)
                // Note: Repair step injection removed for IP protection
                actionQueue.push(testRun.getId(), step);
                retryCounts.put(step.stepId(), retryCount + 1);
            }
            case ReflectionResult.Wait w -> {
                log.info("Waiting: {}", w.reason());
                sleeper.sleep(w.waitMs());
                actionQueue.push(testRun.getId(), step);
                retryCounts.put(step.stepId(), retryCount + 1);
            }
            case ReflectionResult.Abort a -> {
                // Generate AI optimization suggestion for failed step
                String optimizationSuggestion = suggestionService.generateSuggestion(
                        step.action(),
                        step.target(),
                        step.selector().orElse(null),
                        consoleErrors,
                        List.of(), // networkErrors not captured yet
                        before,
                        false
                ).orElse(null);

                ExecutedStep executedStep = ExecutedStep.failed(
                        step,
                        "Aborted: " + a.reason(),
                        before,
                        retryCount,
                        optimizationSuggestion,
                        null,
                        null,
                        consoleErrors,
                        clock.instant());
                int stepIndex = testRun.getExecutedSteps().size();
                saveScreenshot(testRun.getId().value().toString(), stepIndex);
                testRun.recordStepExecution(executedStep, clock.instant());
                testRunRepository.save(testRun);
                doneQueue.record(testRun.getId(), executedStep);
                selectorResolution.recordOutcome(testRun.getTenantId(), smartDriver, false);
                failRun(testRun, "Aborted: " + a.reason());
                retryCounts.remove(step.stepId());
            }
            case ReflectionResult.Skip s -> {
                // Skip this step and continue to next - used for optional steps like cookie consent
                log.info("Skipping optional step: {}", s.reason());
                ExecutedStep executedStep = ExecutedStep.skipped(
                        step,
                        s.reason(),
                        before,
                        retryCount,
                        clock.instant());
                testRun.recordStepExecution(executedStep, clock.instant());
                testRunRepository.save(testRun);
                doneQueue.record(testRun.getId(), executedStep);
                retryCounts.remove(step.stepId());
            }
        }
    }

    /**
     * Proactively detects and clears blocking obstacles (modals, popups, cookie banners).
     *
     * <p>This solves the "human QA would just click the Agree button" problem.
     * The AI now perceives and handles ANY blocking element, not just those it planned for.
     *
     * <p>Loop detection: Uses the dismissedObstacleTypes set to track obstacles that have already
     * been dismissed across the ENTIRE test run, preventing infinite loops where the same
     * obstacle keeps being detected (e.g., ad_feedback elements that CNN keeps showing).
     *
     * @param testRun Current test run
     * @param snapshot Current DOM snapshot
     * @param dismissedObstacleTypes Set of obstacle types already dismissed in this test run (mutated)
     * @return Updated snapshot after clearing obstacles (or original if none found)
     */
    private DomSnapshot clearObstaclesIfPresent(TestRun testRun, DomSnapshot snapshot,
                                                java.util.Set<String> dismissedObstacleTypes) {
        DomSnapshot currentSnapshot = snapshot;
        int obstaclesDismissedThisStep = 0;
        // Track attempts per obstacle type within this step to allow retries before giving up
        Map<String, Integer> attemptsByType = new HashMap<>();
        // Track if we've already tried fallback selectors this step
        boolean triedFallbackSelectors = dismissedObstacleTypes.contains("__fallback_selectors_tried__");

        for (int attempt = 0; attempt < config.getMaxObstacleClearAttempts(); attempt++) {
            Optional<ObstacleDetector.ObstacleInfo> obstacleOpt = obstacleDetector.detect(currentSnapshot);

            if (obstacleOpt.isEmpty()) {
                // AI says no obstacle detected - but try fallback selectors before giving up
                // This handles cases where consent dialogs are in iframes not captured in snapshot
                if (!triedFallbackSelectors) {
                    DomSnapshot afterFallback = tryFallbackConsentSelectors(testRun, currentSnapshot);
                    triedFallbackSelectors = true;
                    dismissedObstacleTypes.add("__fallback_selectors_tried__");

                    // If fallback changed something, continue detection loop
                    if (afterFallback != currentSnapshot) {
                        currentSnapshot = afterFallback;
                        obstaclesDismissedThisStep++;
                        continue;  // Re-run detection to see if more obstacles exist
                    }
                }

                // No obstacle detected and fallbacks didn't help - path is clear
                dismissedObstacleTypes.addAll(attemptsByType.keySet());
                if (obstaclesDismissedThisStep > 0) {
                    log.info("[OBSTACLE] Cleared {} obstacle(s) this step, path is now clear", obstaclesDismissedThisStep);
                }
                return currentSnapshot;
            }

            ObstacleDetector.ObstacleInfo obstacle = obstacleOpt.get();
            String obstacleType = obstacle.obstacleType();

            // Test-run-level loop detection: Skip if we've already dismissed this type in a PREVIOUS step
            if (dismissedObstacleTypes.contains(obstacleType)) {
                log.info("[OBSTACLE] Already dismissed '{}' earlier in this test run - skipping",
                        obstacleType);
                return currentSnapshot;
            }

            // Track attempts for this obstacle type within this step
            int typeAttempts = attemptsByType.getOrDefault(obstacleType, 0);
            if (typeAttempts >= 2) {
                // We've tried twice for this specific type in this step - give up on this type
                log.warn("[OBSTACLE] Failed to dismiss '{}' after {} attempts - marking as handled to prevent loop",
                        obstacleType, typeAttempts);
                dismissedObstacleTypes.add(obstacleType);
                // Continue to see if there are other obstacles
                continue;
            }

            // Skip low confidence detections after first attempt (might be false positive)
            if (typeAttempts > 0 && obstacle.confidence() == ObstacleDetector.Confidence.LOW) {
                log.debug("[OBSTACLE] Skipping low-confidence detection for '{}' after {} attempts",
                        obstacleType, typeAttempts);
                dismissedObstacleTypes.add(obstacleType);
                continue;
            }

            // Use JS click on retry (native click may have been intercepted by overlay)
            boolean useJsClick = typeAttempts > 0;
            log.info("[OBSTACLE] Detected '{}' blocking the page - clicking '{}' to dismiss (attempt {}, {})",
                    obstacleType, obstacle.dismissText(), typeAttempts + 1,
                    useJsClick ? "JS click" : "native click");

            try {
                // Pre-click delay: let animations complete and element become fully interactive
                sleeper.sleep(250);

                // Click the dismiss button
                String selector = obstacle.dismissSelector();
                String dismissText = obstacle.dismissText();
                if (useJsClick) {
                    // JavaScript click with text-based fallback
                    // First try querySelector, then fall back to text matching
                    String jsClickScript = buildJsClickScript(selector, dismissText);
                    getBrowserDriver().callTool("evaluate", Map.of("script", jsClickScript));
                } else {
                    // Native click - preferred when it works
                    Map<String, Object> clickParams = Map.of("selector", selector);
                    getBrowserDriver().callTool("click", clickParams);
                }

                // Increment attempt count (but don't mark as dismissed yet - verify on next loop)
                attemptsByType.put(obstacleType, typeAttempts + 1);
                obstaclesDismissedThisStep++;

                // Wait for the overlay to disappear
                sleeper.sleep(500);

                // Re-snapshot after dismissal
                currentSnapshot = getSnapshot();

                // Record as an auto-generated step (for audit trail)
                ActionStep obstacleAction = ActionStepFactory.withSelector(
                        ActionStepFactory.click("Auto-dismiss: " + obstacleType + (useJsClick ? " (JS)" : "")),
                        selector);
                ExecutedStep obstacleStep = ExecutedStep.success(
                        obstacleAction,
                        selector,
                        snapshot,
                        currentSnapshot,
                        750, // 250ms pre-delay + 500ms post-delay
                        0,
                        null,
                        null,
                        null,
                        List.of(),
                        clock.instant());
                int obstacleStepIndex = testRun.getExecutedSteps().size();
                saveScreenshot(testRun.getId().value().toString(), obstacleStepIndex);
                testRun.recordStepExecution(obstacleStep, clock.instant());
                testRunRepository.save(testRun);

                log.info("[OBSTACLE] {} click sent for '{}', verifying on next detection cycle...",
                        useJsClick ? "JS" : "Native", obstacleType);

            } catch (Exception e) {
                log.warn("[OBSTACLE] Failed to {} click dismiss for '{}': {}",
                        useJsClick ? "JS" : "native", obstacleType, e.getMessage());
                // Increment attempt count even on failure
                attemptsByType.put(obstacleType, typeAttempts + 1);
            }
        }

        // If we reach here, mark all attempted obstacles to prevent infinite retries in future steps
        dismissedObstacleTypes.addAll(attemptsByType.keySet());
        log.warn("[OBSTACLE] Reached max attempts ({}) clearing obstacles. Attempted types: {}",
                config.getMaxObstacleClearAttempts(), attemptsByType.keySet());
        return currentSnapshot;
    }

    /**
     * Builds a JavaScript click script with text-based fallback.
     * First tries the CSS selector, then falls back to finding elements by text content.
     *
     * @param selector CSS selector to try first
     * @param dismissText Text to search for if selector fails
     * @return JavaScript code that attempts to click the element
     */
    private String buildJsClickScript(String selector, String dismissText) {
        String escapedSelector = selector != null ? selector.replace("'", "\\'") : "";
        String escapedText = dismissText != null ? dismissText.replace("'", "\\'") : "";

        // Try CSS selector first, then fall back to text-based matching
        return String.format(
                "(() => {" +
                "  let el = null;" +
                "  try { el = document.querySelector('%s'); } catch(e) { /* invalid selector */ }" +
                "  if (el) { el.click(); return 'clicked'; }" +
                "  // Fallback: find by text content" +
                "  const text = '%s'.toLowerCase();" +
                "  if (!text) return 'not found';" +
                "  const buttons = [...document.querySelectorAll('button, [role=button], a, input[type=button], input[type=submit]')];" +
                "  el = buttons.find(b => {" +
                "    const btnText = (b.textContent || b.value || b.getAttribute('aria-label') || '').toLowerCase().trim();" +
                "    return btnText === text || btnText.includes(text);" +
                "  });" +
                "  if (el) { el.click(); return 'clicked by text'; }" +
                "  return 'not found';" +
                "})()",
                escapedSelector, escapedText);
    }

    /**
     * Tries fallback CSS selectors to dismiss consent dialogs when AI detection fails.
     * This is the "chaos monster" strategy - blindly try clicking common consent button selectors.
     * Useful when consent dialogs are in iframes not captured in the DOM snapshot.
     *
     * @param testRun Current test run
     * @param snapshot Current DOM snapshot
     * @return Updated snapshot if a click succeeded, or original snapshot if nothing worked
     */
    private DomSnapshot tryFallbackConsentSelectors(TestRun testRun, DomSnapshot snapshot) {
        log.info("[OBSTACLE] AI found no obstacle - trying {} fallback consent selectors...",
                FALLBACK_CONSENT_SELECTORS.size());

        for (String selector : FALLBACK_CONSENT_SELECTORS) {
            try {
                // Pre-click delay
                sleeper.sleep(100);

                // Try JS click (more reliable for elements in iframes or with overlays)
                String jsClickScript = String.format(
                        "const el = document.querySelector('%s'); " +
                        "if (el && el.offsetParent !== null) { el.click(); 'clicked'; } else { 'not found'; }",
                        selector.replace("'", "\\'"));

                Map<String, Object> result = getBrowserDriver().callTool("evaluate", Map.of("script", jsClickScript));

                // Check if click succeeded (element was found and clicked)
                String clickResult = extractEvaluateResult(result);
                if ("clicked".equals(clickResult)) {
                    log.info("[OBSTACLE] Fallback selector '{}' clicked successfully!", selector);

                    // Wait for overlay to disappear
                    sleeper.sleep(500);

                    // Re-snapshot
                    DomSnapshot newSnapshot = getSnapshot();

                    // Record as auto-generated step for audit trail
                    ActionStep fallbackAction = ActionStepFactory.withSelector(
                            ActionStepFactory.click("Fallback consent click"),
                            selector);
                    ExecutedStep fallbackStep = ExecutedStep.success(
                            fallbackAction,
                            selector,
                            snapshot,
                            newSnapshot,
                            600, // 100ms pre + 500ms post
                            0,
                            null,
                            null,
                            null,
                            List.of(),
                            clock.instant());
                    int stepIndex = testRun.getExecutedSteps().size();
                    saveScreenshot(testRun.getId().value().toString(), stepIndex);
                    testRun.recordStepExecution(fallbackStep, clock.instant());
                    testRunRepository.save(testRun);

                    return newSnapshot;
                }
            } catch (Exception e) {
                // Selector didn't work, try next one
                log.debug("[OBSTACLE] Fallback selector '{}' failed: {}", selector, e.getMessage());
            }
        }

        log.info("[OBSTACLE] No fallback selectors matched - proceeding without consent dismissal");
        return snapshot;
    }

    /**
     * Extracts the result string from an evaluate tool response.
     */
    private String extractEvaluateResult(Map<String, Object> result) {
        if (result == null) return null;
        Object content = result.get("content");
        if (content instanceof List<?> contentList && !contentList.isEmpty()) {
            Object first = contentList.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                Object text = firstMap.get("text");
                if (text != null) return text.toString().trim();
            }
        }
        return null;
    }

    private String mapActionToTool(String action) {
        return switch (action) {
            case "navigate" -> "navigate_page";
            case "type" -> "fill"; // Tool name is 'fill'
            case "wait" -> "wait_for";
            case "screenshot" -> "take_screenshot";
            case "scroll" -> "evaluate"; // Use JavaScript for scrolling
            case "measure_performance" -> "get_performance_metrics";
            default -> action;
        };
    }

    private Map<String, Object> buildToolParams(ActionStep step) {
        Map<String, Object> params = new HashMap<>();

        if ("navigate".equals(step.action())) {
            step.value().ifPresent(value -> params.put("url", value));
        }

        // Handle scroll action - convert target to JavaScript
        if ("scroll".equals(step.action())) {
            String scrollScript = buildScrollScript(step.target());
            params.put("script", scrollScript);
            return params;
        }

        // Handle measure_performance action - set proper performance tool params
        if ("measure_performance".equals(step.action())) {
            params.put("includeResources", true);
            params.put("resourceThresholdMs", 500);
            params.put("resourceThresholdKb", 100);
            return params;
        }

        step.selector().ifPresent(selector -> params.put("selector", selector));
        step.value().ifPresent(value -> params.put("value", value));
        if (!step.params().isEmpty()) {
            params.putAll(step.params());
        }

        if ("wait".equals(step.action())) {
            normalizeWaitParams(params);
        }

        return params;
    }

    /**
     * Automatically scrolls the page before taking a screenshot if the target implies a specific area.
     * This ensures screenshots capture different parts of the page when targets mention
     * "footer", "bottom", "middle", etc.
     */
    private void autoScrollForScreenshot(String target) {
        if (target == null || target.isBlank()) {
            return; // No target specified, don't scroll
        }

        String normalizedTarget = target.toLowerCase().trim();

        // Check if target implies a specific page area that requires scrolling
        boolean needsScroll = normalizedTarget.contains("bottom")
                || normalizedTarget.contains("footer")
                || normalizedTarget.contains("end")
                || normalizedTarget.contains("middle")
                || normalizedTarget.contains("center")
                || normalizedTarget.contains("below")
                || normalizedTarget.contains("lower")
                || normalizedTarget.contains("section")
                || normalizedTarget.matches(".*\\d+\\s*%.*"); // percentage like "50%"

        if (!needsScroll) {
            log.debug("[AUTO-SCROLL] Target '{}' doesn't require scrolling", target);
            return;
        }

        String scrollScript = buildScrollScript(target);
        log.info("[AUTO-SCROLL] Scrolling before screenshot: target='{}', script='{}'", target, scrollScript);

        try {
            getBrowserDriver().callTool("evaluate", Map.of("script", scrollScript));
            // Small delay to let the page settle after scrolling
            Thread.sleep(500);
        } catch (Exception e) {
            log.warn("[AUTO-SCROLL] Failed to scroll: {}", e.getMessage());
        }
    }

    /**
     * Builds a JavaScript scroll script based on the scroll target description.
     * Supports targets like "bottom", "top", "middle", "footer", percentage, or pixel values.
     */
    private String buildScrollScript(String target) {
        if (target == null || target.isBlank()) {
            return "window.scrollBy(0, window.innerHeight)"; // Default: scroll one viewport
        }

        String normalizedTarget = target.toLowerCase().trim();

        // Handle common scroll targets
        if (normalizedTarget.contains("bottom") || normalizedTarget.contains("footer") || normalizedTarget.contains("end")) {
            return "window.scrollTo(0, document.body.scrollHeight)";
        }
        if (normalizedTarget.contains("top") || normalizedTarget.contains("beginning") || normalizedTarget.contains("start")) {
            return "window.scrollTo(0, 0)";
        }
        if (normalizedTarget.contains("middle") || normalizedTarget.contains("center")) {
            return "window.scrollTo(0, document.body.scrollHeight / 2)";
        }

        // Handle percentage (e.g., "50%", "scroll to 75%")
        java.util.regex.Pattern percentPattern = java.util.regex.Pattern.compile("(\\d+)\\s*%");
        java.util.regex.Matcher percentMatcher = percentPattern.matcher(normalizedTarget);
        if (percentMatcher.find()) {
            int percent = Integer.parseInt(percentMatcher.group(1));
            return String.format("window.scrollTo(0, document.body.scrollHeight * %d / 100)", percent);
        }

        // Handle pixel values (e.g., "500px", "scroll 1000 pixels")
        java.util.regex.Pattern pixelPattern = java.util.regex.Pattern.compile("(\\d+)\\s*(?:px|pixels?)?");
        java.util.regex.Matcher pixelMatcher = pixelPattern.matcher(normalizedTarget);
        if (pixelMatcher.find()) {
            int pixels = Integer.parseInt(pixelMatcher.group(1));
            return String.format("window.scrollTo(0, %d)", pixels);
        }

        // Default: scroll down one viewport height
        return "window.scrollBy(0, window.innerHeight)";
    }

    private void normalizeWaitParams(Map<String, Object> params) {
        boolean hasCondition = params.containsKey("selector")
                || params.containsKey("text")
                || params.containsKey("navigation")
                || params.containsKey("ms");
        Optional<Integer> timeout = Optional.ofNullable(params.get("timeout"))
                .map(Object::toString)
                .flatMap(this::parseInt);
        if (!hasCondition && timeout.isPresent()) {
            params.put("ms", timeout.get());
        }
    }

    private Optional<Integer> parseInt(String value) {
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private SelectorResolution resolveSelector(TestRun testRun, ActionStep step, DomSnapshot snapshot) {
        if (!requiresSelector(step)) {
            return SelectorResolution.resolved(step, Optional.empty());
        }

        if (step.selector().filter(s -> !s.isBlank()).isPresent()) {
            return SelectorResolution.resolved(step, selectorContext(step, snapshot, testRun));
        }

        String elementDescription = Optional.ofNullable(step.target()).orElse("").trim();
        if (elementDescription.isBlank()) {
            return SelectorResolution.unresolved(step, selectorContext(step, snapshot, testRun));
        }

        String url = resolveUrl(snapshot, testRun);
        Optional<SmartDriver.SelectorResult> selectorResult = smartDriver.findElementWithoutVerification(
                testRun.getTenantId(),
                elementDescription,
                url,
                snapshot);

        if (selectorResult.isEmpty()) {
            // Consent button fallback: if AI can't find the element but target looks like a consent button,
            // return a special selector that triggers the text-based consent fallback in Playwright
            if (isConsentButtonTarget(elementDescription)) {
                log.info("AI returned NOT_FOUND for consent target '{}', using CONSENT_FALLBACK", elementDescription);
                String fallbackSelector = "CONSENT_FALLBACK:" + extractConsentButtonText(elementDescription);
                ActionStep fallbackStep = ActionStepFactory.withSelector(step, fallbackSelector);
                return SelectorResolution.resolved(fallbackStep, selectorContext(fallbackStep, snapshot, testRun));
            }
            return SelectorResolution.unresolved(step, selectorContext(step, snapshot, testRun));
        }

        ActionStep resolvedStep = ActionStepFactory.withSelector(step, selectorResult.get().selector());
        return SelectorResolution.resolved(resolvedStep, selectorContext(resolvedStep, snapshot, testRun));
    }

    /**
     * Checks if the target description appears to be a consent/cookie button.
     */
    private boolean isConsentButtonTarget(String target) {
        if (target == null) return false;
        String lower = target.toLowerCase();
        return lower.contains("consent") ||
               lower.contains("cookie") ||
               lower.contains("accept") ||
               lower.contains("agree") ||
               lower.contains("privacy") ||
               lower.contains("gdpr");
    }

    /**
     * Extracts the expected button text from a consent target description.
     */
    private String extractConsentButtonText(String target) {
        if (target == null) return "Accept";
        String lower = target.toLowerCase();
        // Try to find specific button text in the target
        if (lower.contains("i accept")) return "I Accept";
        if (lower.contains("i agree")) return "I Agree";
        if (lower.contains("accept all")) return "Accept All";
        if (lower.contains("agree all")) return "Agree All";
        if (lower.contains("accept")) return "Accept";
        if (lower.contains("agree")) return "Agree";
        if (lower.contains("got it")) return "Got it";
        if (lower.contains("ok")) return "OK";
        if (lower.contains("continue")) return "Continue";
        return "Accept"; // Default
    }

    private boolean requiresSelector(ActionStep step) {
        return switch (step.action()) {
            case "click", "type", "hover" -> true;
            default -> false;
        };
    }

    private Optional<SelectorContext> selectorContext(ActionStep step, DomSnapshot snapshot, TestRun testRun) {
        String description = Optional.ofNullable(step.target()).orElse("").trim();
        if (description.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new SelectorContext(description, resolveUrl(snapshot, testRun)));
    }

    private String resolveUrl(DomSnapshot snapshot, TestRun testRun) {
        String snapshotUrl = Optional.ofNullable(snapshot.url()).orElse("").trim();
        if (!snapshotUrl.isBlank()) {
            return snapshotUrl;
        }
        return testRun.getTargetUrl();
    }

    private void saveScreenshot(String runId, int stepIndex) {
        try {
            Optional<byte[]> screenshot = takeScreenshot();
            if (screenshot.isEmpty()) {
                log.warn("Screenshot capture returned empty for step {} (runId={})", stepIndex, runId);
            } else {
                artifactStorage.saveScreenshot(runId, stepIndex, screenshot.get());
                log.debug("Screenshot saved for step {} (runId={})", stepIndex, runId);
            }
        } catch (Exception e) {
            log.warn("Failed to save screenshot for step {}: {}", stepIndex, e.getMessage());
        }
    }

    private Optional<byte[]> takeScreenshot() {
        try {
            // Use fullPage: false to capture the current viewport (what's visible after scrolling)
            // This ensures screenshots reflect where the test actually navigated/scrolled to
            Map<String, Object> result = Optional.ofNullable(
                    getBrowserDriver().callTool("take_screenshot", Map.of("fullPage", false)))
                    .orElse(Map.of());

            // Debug: log the actual result structure at INFO for visibility
            log.info("[SCREENSHOT] Raw result keys: {}", result.keySet());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
            if (contentList == null || contentList.isEmpty()) {
                log.warn("[SCREENSHOT] No content list. Full result: {}", result);
                return Optional.empty();
            }

            // Debug: log content item details
            log.info("[SCREENSHOT] Content list size: {}", contentList.size());
            contentList.forEach(item -> {
                String itemType = String.valueOf(item.get("type"));
                log.info("[SCREENSHOT] Content item: type='{}', keys={}", itemType, item.keySet());
                // If it's a text type, log a preview of the text content
                if ("text".equals(itemType) && item.get("text") != null) {
                    String text = String.valueOf(item.get("text"));
                    log.info("[SCREENSHOT] Text content preview: {}...",
                            text.substring(0, Math.min(200, text.length())));
                }
            });

            Optional<byte[]> screenshotData = contentList.stream()
                    .filter(item -> "image".equals(item.get("type")))
                    .map(item -> Optional.ofNullable(item.get("data")).map(String::valueOf))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(data -> Base64.getDecoder().decode(data));

            if (screenshotData.isEmpty()) {
                log.warn("[SCREENSHOT] No image data found in content list (type='image' not found)");
            } else {
                log.info("[SCREENSHOT] Successfully extracted {} bytes", screenshotData.get().length);
            }
            return screenshotData;
        } catch (Exception e) {
            log.warn("Exception during screenshot capture: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private DomSnapshot getSnapshot() {
        try {
            Map<String, Object> result = getBrowserDriver().callTool("take_snapshot", Map.of("verbose", false));

            // Debug: log raw result
            log.info("[SNAPSHOT] Raw result keys: {}", result != null ? result.keySet() : "null");

            // MCP response format: { "content": [ { "type": "text", "text": "..." } ] }
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
            if (contentList == null || contentList.isEmpty()) {
                log.warn("[SNAPSHOT] No content list in response. Full result: {}", result);
                return new DomSnapshot("", "", "", clock.instant());
            }

            log.info("[SNAPSHOT] Content list size: {}", contentList.size());
            Map<String, Object> firstContent = contentList.getFirst();
            log.info("[SNAPSHOT] First content item keys: {}", firstContent.keySet());

            String textJson = (String) firstContent.get("text");
            if (textJson == null || textJson.isBlank()) {
                log.warn("[SNAPSHOT] No text content in response. First item: {}", firstContent);
                return new DomSnapshot("", "", "", clock.instant());
            }

            log.info("[SNAPSHOT] Text JSON length: {}, preview: {}...",
                    textJson.length(), textJson.substring(0, Math.min(200, textJson.length())));

            // Parse the JSON: { "content": "...", "url": "...", "title": "..." }
            // Use simple string parsing to avoid adding Jackson dependency here
            String content = extractJsonField(textJson, "content");
            String url = extractJsonField(textJson, "url");
            String title = extractJsonField(textJson, "title");

            log.info("[SNAPSHOT] Parsed: url={}, title={}, contentLength={}",
                    url, title, content.length());

            return new DomSnapshot(content, url, title, clock.instant());
        } catch (Exception e) {
            log.warn("Failed to get snapshot: {}", e.getMessage());
            return new DomSnapshot("", "", "", clock.instant());
        }
    }

    /**
     * Simple JSON field extraction without external dependencies.
     * Expects format: "fieldName":"value" or "fieldName": "value"
     */
    private String extractJsonField(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return "";
        }
        String pattern = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(pattern);
        if (fieldIndex == -1) {
            return "";
        }

        // Find the colon after field name
        int colonIndex = json.indexOf(':', fieldIndex + pattern.length());
        if (colonIndex == -1) {
            return "";
        }

        // Skip whitespace and find opening quote
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }

        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return "";
        }
        valueStart++; // Skip opening quote

        // Find closing quote, handling escaped quotes
        StringBuilder value = new StringBuilder();
        for (int i = valueStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                // Handle escape sequences
                char next = json.charAt(i + 1);
                switch (next) {
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case '"' -> value.append('"');
                    case '\\' -> value.append('\\');
                    default -> value.append(next);
                }
                i++; // Skip escaped character
            } else if (c == '"') {
                break; // End of string
            } else {
                value.append(c);
            }
        }

        return value.toString();
    }

    private record SelectorContext(String elementDescription, String url) {}

    private record SelectorResolution(
            ActionStep step,
            Optional<SelectorContext> context,
            boolean resolved
    ) {
        static SelectorResolution resolved(ActionStep step, Optional<SelectorContext> context) {
            return new SelectorResolution(step, context, true);
        }

        static SelectorResolution unresolved(ActionStep step, Optional<SelectorContext> context) {
            return new SelectorResolution(step, context, false);
        }

        boolean isUnresolved() {
            return !resolved;
        }

        void recordOutcome(String tenantId, SmartDriver smartDriver, boolean success) {
            context.ifPresent(value -> smartDriver.recordOutcome(
                    tenantId,
                    value.elementDescription(),
                    value.url(),
                    success));
        }
    }

    /**
     * Enforces the "Stateless Worker" pattern.
     * Destroys ephemeral state to prevent data leaks between tenants.
     */
    private void cleanup(String runId) {
        try {
            log.info("Cleaning up resources for run: {}", runId);

            BrowserDriverPort driver = getBrowserDriver();
            driver.closeContext();

        } catch (Exception e) {
            log.error("CRITICAL: Failed to clean up context. Force restarting MCP client.", e);
            // Force restart cloud driver
            browserDriver.forceRestart();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> extractLogs(Map<String, Object> toolResult) {
        if (toolResult == null || !toolResult.containsKey("logs")) {
            return List.of();
        }

        try {
            Map<String, Object> logs = (Map<String, Object>) toolResult.get("logs");
            List<String> combined = new ArrayList<>();

            if (logs.containsKey("console")) {
                combined.addAll((List<String>) logs.get("console"));
            }
            if (logs.containsKey("pageErrors")) {
                combined.addAll((List<String>) logs.get("pageErrors"));
            }
            return combined;
        } catch (Exception e) {
            log.warn("Failed to parse logs from tool result", e);
            return List.of();
        }
    }

    /**
     * Extracts performance metrics from the get_performance_metrics tool result.
     *
     * The MCP response format is: {"content": [{"type": "text", "text": "{...json...}"}]}
     * We need to unwrap the content array and parse the JSON string.
     */
    @SuppressWarnings("unchecked")
    private PerformanceMetrics extractPerformanceMetrics(Map<String, Object> toolResult) {
        if (toolResult == null) {
            return null;
        }

        try {
            // First, unwrap the MCP content format
            Map<String, Object> metricsData = unwrapMcpContent(toolResult);
            if (metricsData == null) {
                log.warn("Failed to unwrap MCP content from performance metrics result");
                return null;
            }

            // Check for error response
            if (Boolean.FALSE.equals(metricsData.get("success"))) {
                log.warn("Performance metrics collection failed: {}", metricsData.get("error"));
                return null;
            }

            // Extract webVitals
            Map<String, Double> webVitals = new HashMap<>();
            if (metricsData.containsKey("webVitals")) {
                Map<String, Object> rawVitals = (Map<String, Object>) metricsData.get("webVitals");
                for (Map.Entry<String, Object> entry : rawVitals.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        webVitals.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }

            // Extract navigation timing
            Map<String, Double> navigation = new HashMap<>();
            if (metricsData.containsKey("navigation")) {
                Map<String, Object> rawNav = (Map<String, Object>) metricsData.get("navigation");
                for (Map.Entry<String, Object> entry : rawNav.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        navigation.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
            }

            // Extract totals
            Integer totalResources = metricsData.containsKey("totalResources")
                    ? ((Number) metricsData.get("totalResources")).intValue()
                    : null;
            Integer totalTransferSizeKb = metricsData.containsKey("totalTransferSizeKb")
                    ? ((Number) metricsData.get("totalTransferSizeKb")).intValue()
                    : null;

            // Extract issues
            List<PerformanceMetrics.PerformanceIssue> issues = new ArrayList<>();
            if (metricsData.containsKey("issues")) {
                List<Map<String, Object>> rawIssues = (List<Map<String, Object>>) metricsData.get("issues");
                for (Map<String, Object> rawIssue : rawIssues) {
                    String severity = (String) rawIssue.get("severity");
                    String category = (String) rawIssue.get("category");
                    String message = (String) rawIssue.get("message");
                    Double value = rawIssue.containsKey("value") && rawIssue.get("value") instanceof Number
                            ? ((Number) rawIssue.get("value")).doubleValue()
                            : null;
                    Double threshold = rawIssue.containsKey("threshold") && rawIssue.get("threshold") instanceof Number
                            ? ((Number) rawIssue.get("threshold")).doubleValue()
                            : null;
                    issues.add(new PerformanceMetrics.PerformanceIssue(severity, category, message, value, threshold));
                }
            }

            PerformanceMetrics metrics = new PerformanceMetrics(webVitals, navigation, totalResources, totalTransferSizeKb, issues);

            // Log summary for debugging
            if (metrics.hasMetrics()) {
                log.info("Performance metrics captured: {}", metrics.summary());
            } else {
                log.warn("Performance metrics empty after extraction. Raw data keys: {}", metricsData.keySet());
            }

            return metrics;
        } catch (Exception e) {
            log.warn("Failed to parse performance metrics from tool result: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Unwraps MCP content format to get the actual tool result data.
     *
     * MCP tools return: {"content": [{"type": "text", "text": "{...actual data...}"}]}
     * This method extracts and parses the JSON from the text field.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMcpContent(Map<String, Object> mcpResult) {
        try {
            // Check if this is MCP format with content array
            if (mcpResult.containsKey("content")) {
                List<Map<String, Object>> contentList = (List<Map<String, Object>>) mcpResult.get("content");
                if (contentList != null && !contentList.isEmpty()) {
                    Map<String, Object> firstContent = contentList.get(0);
                    String textJson = (String) firstContent.get("text");
                    if (textJson != null && !textJson.isEmpty()) {
                        // Parse the JSON string to get the actual data
                        return objectMapper.readValue(textJson, new TypeReference<Map<String, Object>>() {});
                    }
                }
                log.warn("MCP content array is empty or has no text");
                return null;
            }

            // If not MCP format, assume it's already unwrapped (for backwards compatibility)
            return mcpResult;
        } catch (Exception e) {
            log.warn("Failed to unwrap MCP content: {}", e.getMessage());
            return null;
        }
    }

    private void failRun(TestRun run, String reason) {
        run.fail(reason, clock.instant());
        testRunRepository.save(run);
    }
}
