package com.ai2qa.application.run;

import com.ai2qa.application.orchestrator.AgentOrchestrator;
import com.ai2qa.application.persona.PersonaRegistry;
import com.ai2qa.application.security.ConcurrentTestLimitService;
import com.ai2qa.application.run.view.ActionStepView;
import com.ai2qa.application.run.view.DomSnapshotView;
import com.ai2qa.application.run.view.ExecutedStepView;
import com.ai2qa.application.run.view.PagedResultView;
import com.ai2qa.application.run.view.PerformanceMetricsView;
import com.ai2qa.application.run.view.RunSummaryView;
import com.ai2qa.application.run.view.TestRunView;
import com.ai2qa.application.security.TargetGuardService;
import com.ai2qa.domain.context.TenantContext;
import com.ai2qa.domain.factory.PageRequestFactory;
import com.ai2qa.domain.model.PageRequest;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.RunSummary;
import com.ai2qa.domain.model.ExecutionMode;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.persona.PersonaDefinition;
import com.ai2qa.domain.repository.TestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Application service for test run orchestration.
 * Controllers should delegate to this service for run creation and lookup.
 */
@Service
public class TestRunService {

    private static final Logger log = LoggerFactory.getLogger(TestRunService.class);

    private final TestRunRepository testRunRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final TargetGuardService targetGuardService;
    private final ConcurrentTestLimitService concurrentTestLimitService;
    private final PersonaRegistry personaRegistry;
    private final TaskExecutor taskExecutor;
    private final Clock clock;

    public TestRunService(
            TestRunRepository testRunRepository,
            AgentOrchestrator agentOrchestrator,
            TargetGuardService targetGuardService,
            ConcurrentTestLimitService concurrentTestLimitService,
            PersonaRegistry personaRegistry,
            TaskExecutor taskExecutor,
            Clock clock) {
        this.testRunRepository = testRunRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.targetGuardService = targetGuardService;
        this.concurrentTestLimitService = concurrentTestLimitService;
        this.personaRegistry = personaRegistry;
        this.taskExecutor = taskExecutor;
        this.clock = clock;
    }

    public PagedResultView<TestRunView> listTestRuns(String tenantId, int page, int size) {
        PageRequest request = PageRequestFactory.create(page, size)
                .orElse(PageRequestFactory.defaultRequest());
        return toPagedResultView(testRunRepository.findByTenantId(tenantId, request));
    }

    public Optional<TestRunView> getTestRun(String id) {
        return testRunRepository.findById(parseTestRunId(id))
                .map(this::toView);
    }

    public boolean deleteTestRun(String id) {
        TestRunId testRunId = parseTestRunId(id);
        return testRunRepository.findById(testRunId)
                .map(run -> {
                    testRunRepository.deleteById(testRunId);
                    log.info("Deleted test run: {}", id);
                    return true;
                })
                .orElse(false);
    }

    public Optional<List<ExecutedStepView>> getTestRunLog(String id) {
        return testRunRepository.findById(parseTestRunId(id))
                .map(TestRun::getExecutedSteps)
                .map(this::toExecutedStepViews);
    }

    public TestRunView createTestRun(
            String tenantId,
            String targetUrl,
            List<String> goals,
            String persona,
            String cookiesJson) {
        return createTestRun(tenantId, targetUrl, goals, persona, cookiesJson, false, null);
    }

    public TestRunView createTestRun(
            String tenantId,
            String targetUrl,
            List<String> goals,
            String persona,
            String cookiesJson,
            boolean notifyOnComplete,
            String notificationEmail) {
        return createTestRun(tenantId, targetUrl, goals, persona, cookiesJson,
                notifyOnComplete, notificationEmail, null, null);
    }

    /**
     * Creates a test run.
     *
     * <p>Always uses CLOUD execution mode.
     *
     * @param tenantId          Tenant ID
     * @param targetUrl         URL to test
     * @param goals             Test goals
     * @param persona           Test persona
     * @param cookiesJson       Optional cookies JSON
     * @param notifyOnComplete  Whether to send email notification
     * @param notificationEmail Optional notification email override
     * @param executionMode     Ignored (always CLOUD)
     * @param agentIdStr        Ignored
     * @return Created test run view
     */
    public TestRunView createTestRun(
            String tenantId,
            String targetUrl,
            List<String> goals,
            String persona,
            String cookiesJson,
            boolean notifyOnComplete,
            String notificationEmail,
            String executionMode,
            String agentIdStr) {

        // Always use CLOUD mode
        ExecutionMode mode = ExecutionMode.CLOUD;

        // Validate target URL
        validateTarget(targetUrl);

        List<String> effectiveGoals = goals == null ? List.of() : goals;
        TestRun testRun = TestRun.create(
                new TestRunId(java.util.UUID.randomUUID()),
                tenantId,
                targetUrl,
                effectiveGoals,
                resolvePersona(persona),
                cookiesJson,
                notifyOnComplete,
                notificationEmail,
                mode,
                null,  // No local agent
                clock.instant());
        testRunRepository.save(testRun);

        log.info("Created test run {} for tenant {}", testRun.getId(), tenantId);
        scheduleExecution(tenantId, testRun);
        return toView(testRun);
    }

    public TestRunView createSmokeTest(
            String tenantId,
            String targetUrl,
            List<String> requestedGoals,
            String persona,
            String cookiesJson) {

        List<String> goals = (requestedGoals == null || requestedGoals.isEmpty())
                ? List.of(
                        "Verify page loads successfully",
                        "Check for any error messages on page",
                        "Take a screenshot of the homepage")
                : requestedGoals;

        return createTestRun(tenantId, targetUrl, goals, persona, cookiesJson);
    }

    private void validateTarget(String targetUrl) {
        targetGuardService.validateTarget(targetUrl);
    }

    private void scheduleExecution(String tenantId, TestRun testRun) {
        String testRunId = testRun.getId().value().toString();

        // Acquire concurrent slot BEFORE scheduling async execution
        // This ensures we check limits synchronously and fail fast
        concurrentTestLimitService.acquire(tenantId, testRunId);

        CompletableFuture.runAsync(() -> {
            try {
                TenantContext.setTenantId(tenantId);
                agentOrchestrator.execute(testRun);
            } catch (Exception e) {
                log.error("Async execution failed for test run " + testRun.getId(), e);
            } finally {
                TenantContext.clear();
                // Always release the slot when execution completes
                concurrentTestLimitService.release(tenantId, testRunId);
            }
        }, taskExecutor);
    }

    private TestPersona resolvePersona(String persona) {
        if (persona == null || persona.isBlank()) {
            return TestPersona.defaultPersona();
        }
        String trimmed = persona.trim();
        if (trimmed.isEmpty()) {
            return TestPersona.defaultPersona();
        }
        for (TestPersona candidate : TestPersona.values()) {
            if (candidate.name().equalsIgnoreCase(trimmed)) {
                return candidate;
            }
        }
        return TestPersona.defaultPersona();
    }

    /**
     * Resolves a persona name to a PersonaDefinition using the PersonaRegistry.
     *
     * <p>This supports DB-only personas (like PERFORMANCE_HAWK) that don't have
     * a corresponding TestPersona enum value.
     *
     * @param persona The persona name (case-insensitive)
     * @return The resolved PersonaDefinition
     */
    public PersonaDefinition resolvePersonaDefinition(String persona) {
        return personaRegistry.resolve(persona);
    }

    private TestRunId parseTestRunId(String id) {
        return new TestRunId(java.util.UUID.fromString(id));
    }

    private PagedResultView<TestRunView> toPagedResultView(com.ai2qa.domain.model.PagedResult<TestRun> result) {
        return new PagedResultView<>(
                result.content().stream().map(this::toView).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    private List<ExecutedStepView> toExecutedStepViews(List<ExecutedStep> steps) {
        List<ExecutedStepView> views = new ArrayList<>(steps.size());
        for (ExecutedStep step : steps) {
            views.add(toView(step));
        }
        return views;
    }

    private TestRunView toView(TestRun testRun) {
        return new TestRunView(
                testRun.getId().value().toString(),
                testRun.getTargetUrl(),
                testRun.getPersona().name(),
                testRun.getStatus().name(),
                testRun.getExecutionMode().name(),
                testRun.getCreatedAt(),
                testRun.getCompletedAt(),
                testRun.getFailureReason(),
                testRun.getSummary().map(this::toView),
                testRun.getSummaryStatus().name(),
                testRun.getProgressPercent(),
                testRun.getExecutedSteps().size(),
                testRun.getPlannedSteps().size()
        );
    }

    private RunSummaryView toView(RunSummary summary) {
        return new RunSummaryView(
                summary.status(),
                summary.goalOverview(),
                summary.outcomeShort(),
                Optional.ofNullable(summary.failureAnalysis()),
                Optional.ofNullable(summary.actionableFix()),
                summary.keyAchievements(),
                toView(summary.healthCheck())
        );
    }

    private RunSummaryView.HealthCheckView toView(RunSummary.HealthCheck healthCheck) {
        if (healthCheck == null) {
            return new RunSummaryView.HealthCheckView(
                    new RunSummaryView.IssueStatsView(0, "N/A"),
                    new RunSummaryView.IssueStatsView(0, "N/A"),
                    "N/A",
                    "No data"
            );
        }
        return new RunSummaryView.HealthCheckView(
                toView(healthCheck.networkIssues()),
                toView(healthCheck.consoleIssues()),
                healthCheck.accessibilityScore(),
                healthCheck.accessibilitySummary()
        );
    }

    private RunSummaryView.IssueStatsView toView(RunSummary.IssueStats stats) {
        if (stats == null) {
            return new RunSummaryView.IssueStatsView(0, "N/A");
        }
        return new RunSummaryView.IssueStatsView(stats.count(), stats.summary());
    }

    private ExecutedStepView toView(ExecutedStep step) {
        // Screenshots are saved for all steps except SKIPPED ones
        // SKIPPED steps are optional steps (like cookie consent) that were not executed
        boolean hasScreenshot = step.status() != ExecutedStep.ExecutionStatus.SKIPPED;

        return new ExecutedStepView(
                toView(step.step()),
                step.status().name(),
                step.executedAt(),
                step.durationMs(),
                Optional.ofNullable(step.selectorUsed()),
                Optional.ofNullable(step.snapshotBefore()).map(this::toView),
                Optional.ofNullable(step.snapshotAfter()).map(this::toView),
                Optional.ofNullable(step.errorMessage()),
                step.retryCount(),
                Optional.ofNullable(step.optimizationSuggestion()),
                step.networkErrors(),
                step.accessibilityWarnings(),
                step.consoleErrors(),
                Optional.ofNullable(step.performanceMetrics()).map(this::toView),
                hasScreenshot
        );
    }

    private PerformanceMetricsView toView(com.ai2qa.domain.model.PerformanceMetrics metrics) {
        List<PerformanceMetricsView.PerformanceIssueView> issues = metrics.issues().stream()
                .map(issue -> new PerformanceMetricsView.PerformanceIssueView(
                        issue.severity(),
                        issue.category(),
                        issue.message(),
                        Optional.ofNullable(issue.value()),
                        Optional.ofNullable(issue.threshold())
                ))
                .toList();

        return new PerformanceMetricsView(
                metrics.webVitals(),
                metrics.navigation(),
                Optional.ofNullable(metrics.totalResources()),
                Optional.ofNullable(metrics.totalTransferSizeKb()),
                issues,
                metrics.summary()
        );
    }

    private ActionStepView toView(ActionStep step) {
        return new ActionStepView(
                step.stepId(),
                step.action(),
                step.target(),
                step.selector(),
                step.value(),
                step.params()
        );
    }

    private DomSnapshotView toView(DomSnapshot snapshot) {
        return new DomSnapshotView(
                snapshot.content(),
                snapshot.url(),
                snapshot.title(),
                snapshot.capturedAt()
        );
    }
}
