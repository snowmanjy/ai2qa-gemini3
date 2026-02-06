package com.ai2qa.domain.model;

import com.ai2qa.domain.result.Result;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * TestRun Aggregate Root.
 *
 * <p>
 * Manages the lifecycle of a test run including planning, execution, and
 * completion.
 * 
 * <p>
 * Per CLAUDE.md: Returns Result instead of throwing exceptions.
 */
public class TestRun {

    private final TestRunId id;
    private final String tenantId;
    private final String targetUrl;
    private final List<String> goals;
    private final TestPersona persona;
    private final String cookiesJson;
    private final boolean notifyOnComplete;
    private final String notificationEmail;
    private final Instant createdAt;
    private final ExecutionMode executionMode;

    private TestRunStatus status;
    private List<ActionStep> plannedSteps;
    private List<ExecutedStep> executedSteps;
    private Instant startedAt;
    private Instant completedAt;
    private String failureReason;
    private RunSummary summary;
    private SummaryStatus summaryStatus;

    /**
     * Private constructor - use factory methods.
     */
    private TestRun(
            TestRunId id,
            String tenantId,
            String targetUrl,
            List<String> goals,
            TestPersona persona,
            String cookiesJson,
            boolean notifyOnComplete,
            String notificationEmail,
            TestRunStatus status,
            Instant createdAt,
            ExecutionMode executionMode) {
        this.id = id;
        this.tenantId = tenantId;
        this.targetUrl = targetUrl;
        this.goals = List.copyOf(goals);
        this.persona = persona;
        this.cookiesJson = cookiesJson;
        this.notifyOnComplete = notifyOnComplete;
        this.notificationEmail = notificationEmail;
        this.status = status;
        this.createdAt = createdAt;
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.CLOUD;
        this.plannedSteps = new ArrayList<>();
        this.executedSteps = new ArrayList<>();
        this.summaryStatus = SummaryStatus.PENDING;
    }

    /**
     * Factory method to create a new test run with default STANDARD persona.
     */
    public static TestRun create(TestRunId id, String tenantId, String targetUrl, List<String> goals, Instant now) {
        return create(id, tenantId, targetUrl, goals, TestPersona.STANDARD, null, false, null,
                ExecutionMode.CLOUD, null, now);
    }

    /**
     * Factory method to create a new test run with a specific persona.
     */
    public static TestRun create(TestRunId id, String tenantId, String targetUrl, List<String> goals,
                                  TestPersona persona, Instant now) {
        return create(id, tenantId, targetUrl, goals, persona, null, false, null,
                ExecutionMode.CLOUD, null, now);
    }

    /**
     * Factory method to create a new test run with persona and cookies for authenticated testing.
     */
    public static TestRun create(TestRunId id, String tenantId, String targetUrl, List<String> goals,
                                  TestPersona persona, String cookiesJson, Instant now) {
        return create(id, tenantId, targetUrl, goals, persona, cookiesJson, false, null,
                ExecutionMode.CLOUD, null, now);
    }

    /**
     * Factory method to create a new test run with all options including notifications.
     */
    public static TestRun create(TestRunId id, String tenantId, String targetUrl, List<String> goals,
                                  TestPersona persona, String cookiesJson,
                                  boolean notifyOnComplete, String notificationEmail, Instant now) {
        return create(id, tenantId, targetUrl, goals, persona, cookiesJson, notifyOnComplete, notificationEmail,
                ExecutionMode.CLOUD, null, now);
    }

    /**
     * Factory method to create a new test run with execution mode.
     * Local agent support removed - agentId parameter ignored.
     */
    public static TestRun create(TestRunId id, String tenantId, String targetUrl, List<String> goals,
                                  TestPersona persona, String cookiesJson,
                                  boolean notifyOnComplete, String notificationEmail,
                                  ExecutionMode executionMode, Object ignoredAgentId, Instant now) {
        TestRun testRun = new TestRun(
                id,
                tenantId,
                targetUrl,
                goals,
                persona != null ? persona : TestPersona.STANDARD,
                cookiesJson,
                notifyOnComplete,
                notificationEmail,
                TestRunStatus.PENDING,
                now,
                executionMode);
        return testRun;
    }

    /**
     * Reconstitutes a TestRun from persistence.
     */
    public static TestRun reconstitute(
            TestRunId id,
            String tenantId,
            String targetUrl,
            List<String> goals,
            TestPersona persona,
            String cookiesJson,
            boolean notifyOnComplete,
            String notificationEmail,
            TestRunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            List<ActionStep> plannedSteps,
            List<ExecutedStep> executedSteps,
            String failureReason,
            RunSummary summary) {
        return reconstitute(id, tenantId, targetUrl, goals, persona, cookiesJson,
                notifyOnComplete, notificationEmail, status, createdAt, startedAt, completedAt,
                plannedSteps, executedSteps, failureReason, summary, SummaryStatus.PENDING,
                ExecutionMode.CLOUD);
    }

    /**
     * Reconstitutes a TestRun from persistence with execution mode.
     * Local agent support removed.
     */
    public static TestRun reconstitute(
            TestRunId id,
            String tenantId,
            String targetUrl,
            List<String> goals,
            TestPersona persona,
            String cookiesJson,
            boolean notifyOnComplete,
            String notificationEmail,
            TestRunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            List<ActionStep> plannedSteps,
            List<ExecutedStep> executedSteps,
            String failureReason,
            RunSummary summary,
            SummaryStatus summaryStatus,
            ExecutionMode executionMode) {
        TestRun testRun = new TestRun(id, tenantId, targetUrl, goals,
                persona != null ? persona : TestPersona.STANDARD, cookiesJson,
                notifyOnComplete, notificationEmail, status, createdAt,
                executionMode);
        testRun.startedAt = startedAt;
        testRun.completedAt = completedAt;
        testRun.plannedSteps = new ArrayList<>(plannedSteps);
        testRun.executedSteps = new ArrayList<>(executedSteps);
        testRun.failureReason = failureReason;
        testRun.summary = summary;
        testRun.summaryStatus = summaryStatus != null ? summaryStatus : SummaryStatus.PENDING;
        return testRun;
    }

    // ==================== Commands (Return Result instead of throwing)
    // ====================

    /**
     * Starts the test run with a plan.
     *
     * @param plan The action steps to execute
     * @param now  Current timestamp
     * @return Result indicating success or failure reason
     */
    public Result<Void> start(List<ActionStep> plan, Instant now) {
        if (status != TestRunStatus.PENDING) {
            return Result.failure("Can only start a PENDING test run, current status: " + status);
        }

        this.plannedSteps = new ArrayList<>(plan);
        this.status = TestRunStatus.RUNNING;
        this.startedAt = now;

        return Result.ok();
    }

    /**
     * Records a step execution result.
     *
     * @param executedStep The completed step
     * @param now          Current timestamp
     * @return Result indicating success or failure reason
     */
    public Result<Void> recordStepExecution(ExecutedStep executedStep, Instant now) {
        if (!status.isActive()) {
            return Result.failure("Cannot execute steps in status: " + status);
        }

        executedSteps.add(executedStep);

        // Auto-complete if all steps executed successfully
        if (executedSteps.size() >= plannedSteps.size()) {
            boolean allSuccess = executedSteps.stream()
                    .allMatch(ExecutedStep::isSuccess);
            if (allSuccess) {
                complete(now);
            }
        }

        return Result.ok();
    }

    /**
     * Marks the test run as complete.
     */
    public void complete(Instant now) {
        if (status.isTerminal()) {
            return; // Already terminal
        }

        this.status = TestRunStatus.COMPLETED;
        this.completedAt = now;
    }

    /**
     * Marks the test run as failed.
     */
    public void fail(String reason, Instant now) {
        if (status.isTerminal()) {
            return;
        }

        this.status = TestRunStatus.FAILED;
        this.completedAt = now;
        this.failureReason = reason;

        long successCount = executedSteps.stream()
                .filter(ExecutedStep::isSuccess)
                .count();
    }

    /**
     * Cancels the test run.
     */
    public void cancel(Instant now) {
        if (!status.canCancel()) {
            return;
        }

        this.status = TestRunStatus.CANCELLED;
        this.completedAt = now;
    }

    /**
     * Adds repair steps to the front of the plan (for self-healing).
     *
     * @param repairSteps The steps to add
     * @return Result indicating success or failure reason
     */
    public Result<Void> addRepairSteps(List<ActionStep> repairSteps) {
        if (!status.isActive()) {
            return Result.failure("Cannot add repair steps in status: " + status);
        }

        // Insert at the beginning of remaining steps
        int executedCount = executedSteps.size();
        List<ActionStep> remaining = new ArrayList<>(plannedSteps.subList(executedCount, plannedSteps.size()));

        plannedSteps = new ArrayList<>();
        plannedSteps.addAll(executedSteps.stream().map(ExecutedStep::step).toList());
        plannedSteps.addAll(repairSteps);
        plannedSteps.addAll(remaining);

        return Result.ok();
    }

    // ==================== Queries ====================

    public TestRunId getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public List<String> getGoals() {
        return goals;
    }

    public TestPersona getPersona() {
        return persona;
    }

    public Optional<String> getCookiesJson() {
        return Optional.ofNullable(cookiesJson);
    }

    /**
     * Returns true if this run has cookies for authenticated testing.
     */
    public boolean hasCookies() {
        return cookiesJson != null && !cookiesJson.isBlank();
    }

    /**
     * Returns true if email notification should be sent on completion.
     */
    public boolean isNotifyOnComplete() {
        return notifyOnComplete;
    }

    /**
     * Returns the notification email override for this run, if any.
     * Falls back to tenant's notification email if not set.
     */
    public Optional<String> getNotificationEmail() {
        return Optional.ofNullable(notificationEmail);
    }

    public TestRunStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public Optional<Instant> getStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public List<ActionStep> getPlannedSteps() {
        return Collections.unmodifiableList(plannedSteps);
    }

    public List<ExecutedStep> getExecutedSteps() {
        return Collections.unmodifiableList(executedSteps);
    }

    public Optional<String> getFailureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Optional<RunSummary> getSummary() {
        return Optional.ofNullable(summary);
    }

    /**
     * Sets the summary for this test run.
     * Called by ReportGenerationListener after generating the summary.
     * Automatically sets summaryStatus to COMPLETED.
     */
    public void setSummary(RunSummary summary) {
        this.summary = summary;
        this.summaryStatus = SummaryStatus.COMPLETED;
    }

    /**
     * Returns the current status of summary generation.
     */
    public SummaryStatus getSummaryStatus() {
        return summaryStatus != null ? summaryStatus : SummaryStatus.PENDING;
    }

    /**
     * Marks summary generation as in progress.
     * Called when the AI summary generation starts.
     */
    public void markSummaryGenerating() {
        this.summaryStatus = SummaryStatus.GENERATING;
    }

    /**
     * Marks summary generation as failed.
     * Called when AI summary generation fails or times out.
     */
    public void markSummaryFailed() {
        this.summaryStatus = SummaryStatus.FAILED;
    }

    /**
     * Returns the next step to execute, if any.
     */
    public Optional<ActionStep> getNextStep() {
        if (!status.isActive() || executedSteps.size() >= plannedSteps.size()) {
            return Optional.empty();
        }
        return Optional.of(plannedSteps.get(executedSteps.size()));
    }

    /**
     * Returns progress as a percentage.
     */
    public int getProgressPercent() {
        if (plannedSteps.isEmpty()) {
            return 0;
        }
        return (int) ((executedSteps.size() * 100.0) / plannedSteps.size());
    }

    /**
     * Calculates total duration in milliseconds.
     */
    /**
     * Calculates total duration in milliseconds.
     */
    public long calculateDurationMs(Instant now) {
        if (startedAt == null) {
            return 0;
        }
        Instant end = completedAt != null ? completedAt : now;
        return end.toEpochMilli() - startedAt.toEpochMilli();
    }
}
