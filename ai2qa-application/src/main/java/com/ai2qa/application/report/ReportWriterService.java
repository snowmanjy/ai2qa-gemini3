package com.ai2qa.application.report;

import com.ai2qa.application.ai.PromptTemplates;
import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.RunSummary;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for generating structured report summaries using AI.
 *
 * <p>Uses The Reporter persona to create enterprise-grade summaries
 * with consistent structure for both success and failure cases.
 *
 * <p>Protected by a circuit breaker to prevent cascading failures
 * when the AI service is degraded or unresponsive.
 */
@Service
public class ReportWriterService {

    private static final Logger log = LoggerFactory.getLogger(ReportWriterService.class);

    /**
     * Custom timeout for summary generation (300 seconds / 5 minutes).
     *
     * <p>Summary generation is critical for report value - a fallback summary
     * is much less useful than an AI-generated one. We use a generous timeout
     * because:
     * <ul>
     *   <li>Summary is the final step - user is already invested in waiting</li>
     *   <li>Large test runs can have substantial context to analyze</li>
     *   <li>AI API can be slow during high load periods</li>
     *   <li>Circuit breaker protects against cascading failures anyway</li>
     * </ul>
     */
    private static final long SUMMARY_TIMEOUT_SECONDS = 300;

    private final ChatClientPort chatClient;
    private final ObjectMapper objectMapper;

    public ReportWriterService(
            @Qualifier("plannerChatPort") ChatClientPort chatClient,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a structured summary for a completed test run.
     *
     * <p>Protected by circuit breaker "aiService" to fail fast when
     * the AI provider is degraded. When the circuit is open, returns
     * a fallback summary immediately without attempting the AI call.
     *
     * @param testRun The completed test run
     * @return RunSummary with structured fields, or a fallback summary on error
     */
    @CircuitBreaker(name = "aiService", fallbackMethod = "generateSummaryFallback")
    public RunSummary generateSummary(TestRun testRun) {
        try {
            boolean isSuccess = testRun.getStatus() == TestRunStatus.COMPLETED;
            List<String> stepSummaries = extractStepSummaries(testRun.getExecutedSteps());

            int networkErrorCount = countNetworkErrors(testRun.getExecutedSteps());
            int consoleErrorCount = countConsoleErrors(testRun.getExecutedSteps());
            int accessibilityWarningCount = countAccessibilityWarnings(testRun.getExecutedSteps());

            String prompt = PromptTemplates.reportSummaryPrompt(
                    isSuccess,
                    testRun.getGoals(),
                    stepSummaries,
                    testRun.getFailureReason().orElse(null),
                    networkErrorCount,
                    consoleErrorCount,
                    accessibilityWarningCount
            );

            log.info("[SUMMARY] Generating AI summary with {}s timeout, prompt size: {} chars",
                    SUMMARY_TIMEOUT_SECONDS, prompt.length());

            String response = chatClient.callWithTimeout(
                    PromptTemplates.REPORTER_SYSTEM_PROMPT,
                    prompt,
                    SUMMARY_TIMEOUT_SECONDS
            );

            return parseResponse(response, isSuccess, testRun);

        } catch (Exception e) {
            log.error("Failed to generate AI summary, using fallback", e);
            return createFallbackSummary(testRun);
        }
    }

    /**
     * Fallback method called when circuit breaker is open or AI call fails.
     *
     * @param testRun The test run to summarize
     * @param throwable The exception that triggered the fallback
     * @return A fallback summary without AI-generated content
     */
    @SuppressWarnings("unused") // Called by Resilience4j via reflection
    private RunSummary generateSummaryFallback(TestRun testRun, Throwable throwable) {
        String reason = throwable != null ? throwable.getClass().getSimpleName() : "Unknown";
        log.warn("[CIRCUIT BREAKER] AI summary fallback triggered. Reason: {}, Message: {}",
                reason, throwable != null ? throwable.getMessage() : "N/A");
        return createFallbackSummary(testRun);
    }

    /**
     * Extracts one-line summaries from executed steps.
     * Internal selectors are sanitized before reporting.
     */
    private List<String> extractStepSummaries(List<ExecutedStep> steps) {
        List<String> summaries = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            ExecutedStep step = steps.get(i);
            String status = step.isSuccess() ? "+" : "x";
            String target = sanitizeTargetForReport(step.target());
            summaries.add(String.format("%d. %s %s on %s",
                    i + 1, status, step.action(), target));
        }
        return summaries;
    }

    /**
     * Sanitizes internal/technical selectors to user-friendly descriptions for reports.
     */
    private String sanitizeTargetForReport(String target) {
        if (target == null) {
            return "unknown";
        }
        if (target.startsWith("CONSENT_FALLBACK:")) {
            String buttonText = target.substring("CONSENT_FALLBACK:".length());
            return "consent button (\"" + buttonText + "\")";
        }
        return target;
    }

    /**
     * Parses AI response JSON into RunSummary.
     */
    private RunSummary parseResponse(String response, boolean isSuccess, TestRun testRun) {
        try {
            // Strip markdown code blocks if present
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            JsonNode node = objectMapper.readTree(json);

            List<String> achievements = new ArrayList<>();
            JsonNode achievementsNode = node.get("keyAchievements");
            if (achievementsNode != null && achievementsNode.isArray()) {
                for (JsonNode item : achievementsNode) {
                    achievements.add(item.asText());
                }
            }

            // Parse HealthCheck
            JsonNode healthCheckNode = node.path("healthCheck");
            RunSummary.HealthCheck healthCheck = new RunSummary.HealthCheck(
                    new RunSummary.IssueStats(
                            healthCheckNode.path("networkIssues").path("count").asInt(0),
                            healthCheckNode.path("networkIssues").path("summary").asText("No network issues detected")
                    ),
                    new RunSummary.IssueStats(
                            healthCheckNode.path("consoleIssues").path("count").asInt(0),
                            healthCheckNode.path("consoleIssues").path("summary").asText("No console errors detected")
                    ),
                    healthCheckNode.path("accessibilityScore").asText("N/A"),
                    healthCheckNode.path("accessibilitySummary").asText("No accessibility data available")
            );

            if (isSuccess) {
                return RunSummary.success(
                        node.path("goalOverview").asText("Test execution completed"),
                        node.path("outcomeShort").asText("All steps executed successfully"),
                        achievements,
                        healthCheck
                );
            } else {
                return RunSummary.failure(
                        node.path("goalOverview").asText("Test execution attempted"),
                        node.path("outcomeShort").asText("Test failed during execution"),
                        node.path("failureAnalysis").asText(testRun.getFailureReason().orElse("Unknown failure")),
                        node.path("actionableFix").asText("Review the failed step for potential fixes"),
                        achievements,
                        healthCheck
                );
            }

        } catch (Exception e) {
            log.warn("Failed to parse AI response, using fallback: {}", e.getMessage());
            return createFallbackSummary(testRun);
        }
    }

    /**
     * Creates a fallback summary when AI generation fails.
     */
    private RunSummary createFallbackSummary(TestRun testRun) {
        String goalsOverview = String.join(", ", testRun.getGoals());
        List<String> achievements = testRun.getExecutedSteps().stream()
                .filter(ExecutedStep::isSuccess)
                .limit(5)
                .map(step -> step.action() + " on " + step.target())
                .toList();

        RunSummary.HealthCheck fallbackHealth = new RunSummary.HealthCheck(
                new RunSummary.IssueStats(0, "Data unavailable"),
                new RunSummary.IssueStats(0, "Data unavailable"),
                "N/A",
                "Analysis failed"
        );

        if (testRun.getStatus() == TestRunStatus.COMPLETED) {
            return RunSummary.success(
                    goalsOverview,
                    "Test run completed successfully",
                    achievements,
                    fallbackHealth
            );
        } else {
            return RunSummary.failure(
                    goalsOverview,
                    "Test run failed",
                    testRun.getFailureReason().orElse("Execution error"),
                    "Review the execution log for details",
                    achievements,
                    fallbackHealth
            );
        }
    }

    private int countNetworkErrors(List<ExecutedStep> steps) {
        return steps.stream()
                .mapToInt(step -> step.networkErrors() != null ? step.networkErrors().size() : 0)
                .sum();
    }

    private int countConsoleErrors(List<ExecutedStep> steps) {
        return steps.stream()
                .mapToInt(step -> step.consoleErrors() != null ? step.consoleErrors().size() : 0)
                .sum();
    }

    private int countAccessibilityWarnings(List<ExecutedStep> steps) {
        return steps.stream()
                .mapToInt(step -> step.accessibilityWarnings() != null ? step.accessibilityWarnings().size() : 0)
                .sum();
    }
}
