package com.ai2qa.application.orchestrator;

import com.ai2qa.application.cache.SmartDriver;
import com.ai2qa.application.planner.StepPlanner;
import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.application.port.BrowserDriverPort;
import com.ai2qa.application.port.OrchestratorConfigProvider;
import com.ai2qa.application.security.PlanSanitizer;
import com.ai2qa.application.security.PromptInjectionDetector;
import com.ai2qa.application.util.Sleeper;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.port.ActionQueuePort;
import com.ai2qa.domain.port.DoneQueuePort;
import com.ai2qa.domain.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorSecurityTest {

    @Mock
    private BrowserDriverPort browserDriver;
    @Mock
    private ActionQueuePort actionQueue;
    @Mock
    private DoneQueuePort doneQueue;
    @Mock
    private TestRunRepository testRunRepository;
    @Mock
    private StepPlanner stepPlanner;
    @Mock
    private Reflector reflector;
    @Mock
    private ObstacleDetector obstacleDetector;
    @Mock
    private OptimizationSuggestionService suggestionService;
    @Mock
    private OrchestratorConfigProvider config;
    @Mock
    private Sleeper sleeper;
    @Mock
    private Clock clock;
    @Mock
    private PromptInjectionDetector promptInjectionDetector;
    @Mock
    private PlanSanitizer planSanitizer;
    @Mock
    private SmartDriver smartDriver;
    @Mock
    private ArtifactStorage artifactStorage;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentOrchestrator agentOrchestrator;

    private TestRun testRun;
    private final Instant now = Instant.parse("2024-01-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(now);
        lenient().when(config.getTestTimeoutMinutes()).thenReturn(30);
        testRun = TestRun.create(
                new TestRunId(UUID.randomUUID()),
                "tenant-1",
                "http://example.com",
                List.of("Goal 1"),
                now);
    }

    @Test
    void execute_WhenPromptInjectionDetected_ShouldFailTestRun() {
        // Arrange
        when(promptInjectionDetector.areSafe(any())).thenReturn(false);

        // Act
        agentOrchestrator.execute(testRun);

        // Assert
        verify(testRunRepository, atLeastOnce()).save(testRun);
        assert (testRun.getStatus() == TestRunStatus.FAILED);
        assert (testRun.getFailureReason().orElse("").contains("Prompt injection detected"));

        // Should not proceed to planning or execution
        verify(stepPlanner, never()).createPlan(any(), any(), any(TestPersona.class));
        verify(browserDriver, never()).start();
    }

    @Test
    void execute_WhenPlanUnsafe_ShouldFailTestRun() {
        // Arrange
        when(promptInjectionDetector.areSafe(any())).thenReturn(true);
                when(browserDriver.isRunning()).thenReturn(true);
        List<ActionStep> unsafePlan = List.of(ActionStepFactory.navigate("http://evil.com"));
        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(unsafePlan);
        // Sanitize returns the plan, but isSafe rejects it
        when(planSanitizer.sanitize(anyList(), anyString())).thenReturn(unsafePlan);
        when(planSanitizer.isSafe(eq(unsafePlan), any())).thenReturn(false);

        // Act
        agentOrchestrator.execute(testRun);

        // Assert
        verify(testRunRepository, atLeastOnce()).save(testRun);
        assert (testRun.getStatus() == TestRunStatus.FAILED);
        assert (testRun.getFailureReason().orElse("").toLowerCase().contains("unsafe actions"));

        // Should not execute loop
        verify(actionQueue, never()).pushAll(any(), any());
    }

    @Test
    void execute_WhenSafe_ShouldProceed() {
        // Arrange
        when(promptInjectionDetector.areSafe(any())).thenReturn(true);
                when(browserDriver.isRunning()).thenReturn(true);
        List<ActionStep> safePlan = List.of(ActionStepFactory.navigate("http://example.com"));
        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(safePlan);
        // Sanitize returns the plan unchanged, and isSafe approves it
        when(planSanitizer.sanitize(anyList(), anyString())).thenReturn(safePlan);
        when(planSanitizer.isSafe(eq(safePlan), any())).thenReturn(true);

        // Mock start result
        // testRun.start() is called inside execute(). Since TestRun is not a mock, it
        // will execute real logic.
        // We just verify it saves.

        // Act
        agentOrchestrator.execute(testRun);

        // Assert
        verify(actionQueue).pushAll(eq(testRun.getId()), eq(safePlan));
        verify(testRunRepository, atLeastOnce()).save(testRun);
    }
}
