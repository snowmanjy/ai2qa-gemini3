package com.ai2qa.web.dto;

import com.ai2qa.application.run.view.TestRunView;

import java.time.Instant;
import java.util.Optional;

public record TestRunResponse(
        String id,
        String targetUrl,
        String persona,
        String status,
        String executionMode,
        Instant createdAt,
        Optional<Instant> completedAt,
        Optional<String> failureReason,
        Optional<RunSummaryResponse> summary,
        String summaryStatus,
        int progressPercent,
        int executedStepCount,
        int totalStepCount) {

    public TestRunResponse {
        if (completedAt == null) {
            completedAt = Optional.empty();
        }
        if (failureReason == null) {
            failureReason = Optional.empty();
        }
        if (summary == null) {
            summary = Optional.empty();
        }
    }

    public static TestRunResponse from(TestRunView testRun) {
        return new TestRunResponse(
                testRun.id(),
                testRun.targetUrl(),
                testRun.persona(),
                testRun.status(),
                testRun.executionMode(),
                testRun.createdAt(),
                testRun.completedAt(),
                testRun.failureReason(),
                testRun.summary().map(RunSummaryResponse::from),
                testRun.summaryStatus(),
                testRun.progressPercent(),
                testRun.executedStepCount(),
                testRun.totalStepCount());
    }
}
