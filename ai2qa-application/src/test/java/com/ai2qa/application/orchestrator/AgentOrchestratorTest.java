package com.ai2qa.application.orchestrator;

import com.ai2qa.application.cache.SmartDriver;
import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.application.planner.StepPlanner;
import com.ai2qa.application.security.PlanSanitizer;
import com.ai2qa.application.security.PromptInjectionDetector;
import com.ai2qa.application.util.Sleeper;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.port.ActionQueuePort;
import com.ai2qa.domain.port.DoneQueuePort;
import com.ai2qa.domain.repository.TestRunRepository;
import com.ai2qa.domain.result.Result;
import com.ai2qa.application.port.BrowserDriverPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AgentOrchestrator.
 * 
 * <p>
 * Tests all logic branches including:
 * <ul>
 * <li>Pre-flight checks (injection, credits, exceptions)</li>
 * <li>Planning phase (unsafe plan rejection)</li>
 * <li>Execution loop (success, retry, wait, abort, step exception)</li>
 * <li>Cleanup in finally block (normal and exception paths)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

        @Mock
        private StepPlanner stepPlanner;
        @Mock
        private BrowserDriverPort browserDriver;
        @Mock
        private ActionQueuePort actionQueue;
        @Mock
        private DoneQueuePort doneQueue;
        @Mock
        private TestRunRepository testRunRepository;
        @Mock
        private SmartDriver smartDriver;
        @Mock
        private ArtifactStorage artifactStorage;
        @Mock
        private PromptInjectionDetector promptInjectionDetector;
        @Mock
        private PlanSanitizer planSanitizer;
        @Mock
        private Reflector reflector;
        @Mock
        private ObstacleDetector obstacleDetector;
        @Mock
        private OptimizationSuggestionService suggestionService;
        @Mock
        private Sleeper sleeper;
        @Mock
        private ApplicationEventPublisher eventPublisher;

        private Clock fixedClock;
        private AgentOrchestrator orchestrator;
        private TestRun testRun;

        private static final Instant NOW = Instant.parse("2024-01-15T10:00:00Z");
        private static final String TENANT_ID = "tenant-123";
        private static final String TARGET_URL = "https://example.com";

        @BeforeEach
        void setUp() {
                fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
                orchestrator = new AgentOrchestrator(
                                stepPlanner,
                                browserDriver,
                                actionQueue,
                                doneQueue,
                                testRunRepository,
                                smartDriver,
                                artifactStorage,
                                promptInjectionDetector,
                                planSanitizer,
                                reflector,
                                obstacleDetector,
                                suggestionService,
                                new TestOrchestratorConfig(),
                                fixedClock,
                                sleeper,
                                eventPublisher);

                testRun = TestRun.create(
                                new TestRunId(UUID.randomUUID()),
                                TENANT_ID,
                                TARGET_URL,
                                List.of("Click login button", "Enter credentials"),
                                NOW);

                lenient().when(smartDriver.findElementWithoutVerification(anyString(), anyString(), anyString(), any()))
                                .thenReturn(Optional.of(SmartDriver.SelectorResult.fromAi("button#login")));
                lenient().when(browserDriver.callTool(eq("take_screenshot"), any()))
                                .thenReturn(Map.of(
                                                "content",
                                                List.of(Map.of(
                                                                "type", "image",
                                                                "data", "ZGF0YQ=="))));
                lenient().when(suggestionService.generateSuggestion(anyString(), anyString(), any(), anyList(), anyList(), any(), anyBoolean()))
                                .thenReturn(Optional.empty());
        }

        // ========== PRE-FLIGHT CHECKS ==========

        @Nested
        @DisplayName("Pre-flight Checks")
        class PreFlightChecks {

                @Test
                @DisplayName("When prompt injection detected, should fail run immediately without cleanup")
                void execute_WhenPromptInjectionDetected_FailsRunWithoutCleanup() {
                        // Arrange
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(false);

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                        reason -> assertThat(reason).contains("Prompt injection detected"));

                        verify(testRunRepository).save(testRun);
                        verify(stepPlanner, never()).createPlan(any(), any(), any(TestPersona.class));
                        verify(browserDriver, never()).start();
                        verify(browserDriver, never()).closeContext(); // No cleanup needed
                }

                @Test
                @DisplayName("When pre-flight check throws exception, should fail run without cleanup")
                void execute_WhenPreFlightThrows_FailsRunWithoutCleanup() {
                        // Arrange
                        when(promptInjectionDetector.areSafe(anyList()))
                                        .thenThrow(new RuntimeException("Detector service unavailable"));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                        reason -> assertThat(reason).contains("SYSTEM_ERROR: Exception during pre-checks"));

                        verify(browserDriver, never()).closeContext();
                }
        }

        // ========== PLANNING PHASE ==========

        @Nested
        @DisplayName("Planning Phase")
        class PlanningPhase {

                @BeforeEach
                void setUpPassingPreFlight() {
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);
                }

                @Test
                @DisplayName("When plan is unsafe, should fail run and cleanup")
                void execute_WhenPlanUnsafe_FailsRunAndCleansUp() {
                        // Arrange
                        List<ActionStep> unsafePlan = List.of(ActionStepFactory.navigate("http://evil.com"));
                        when(stepPlanner.createPlan(TARGET_URL, testRun.getGoals(), testRun.getPersona())).thenReturn(unsafePlan);
                        when(planSanitizer.sanitize(anyList(), anyString())).thenReturn(unsafePlan);
                        when(planSanitizer.isSafe(unsafePlan, TARGET_URL)).thenReturn(false);

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                        reason -> assertThat(reason).containsIgnoringCase("unsafe actions"));

                        // Cleanup should still run
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When McpClient not running, should start it")
                void execute_WhenMcpNotRunning_StartsClient() {
                        // Arrange
                        when(browserDriver.isRunning()).thenReturn(false);
                        List<ActionStep> plan = List.of(ActionStepFactory.navigate(TARGET_URL));
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(plan);
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        InOrder inOrder = inOrder(browserDriver);
                        inOrder.verify(browserDriver).start();
                        inOrder.verify(browserDriver).createContext(anyBoolean(), anyString());
                }
        }

        // ========== EXECUTION LOOP ==========

        @Nested
        @DisplayName("Execution Loop")
        class ExecutionLoop {

                private ActionStep clickStep;
                private DomSnapshot beforeSnapshot;
                private DomSnapshot afterSnapshot;

                @BeforeEach
                void setUpSuccessfulPlanningPhase() {
                        // Pass all pre-flight and planning checks
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);

                        clickStep = ActionStepFactory.click("login button");
                        List<ActionStep> plan = List.of(clickStep);
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(plan);
                        // Mock both sanitize (returns plan unchanged) and isSafe (returns true)
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                        // Setup snapshots
                        beforeSnapshot = new DomSnapshot("before", "url", "title", NOW);
                        afterSnapshot = new DomSnapshot("after", "url", "title", NOW);

                        String snapshotPayload = "{\"content\":\"snapshot\",\"url\":\"url\",\"title\":\"title\"}";
                        lenient().when(browserDriver.callTool(eq("take_snapshot"), any()))
                                        .thenReturn(Map.of(
                                                        "content",
                                                        List.of(Map.of("type", "text", "text", snapshotPayload))));
                }

                @Test
                @DisplayName("When reflection returns Success, should record step and update cache")
                void execute_WhenReflectionSuccess_RecordsStepAndUpdatesCache() {
                        // Arrange
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep))
                                        .thenReturn(Optional.empty());

                        when(reflector.reflect(any(ActionStep.class), any(), any(), isNull(), eq(0)))
                                        .thenReturn(ReflectionResult.success("#login-btn"));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(doneQueue).record(eq(testRun.getId()), any(ExecutedStep.class));
                        verify(smartDriver).recordOutcome(eq(TENANT_ID), eq("login button"), eq("url"), eq(true));
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When reflection returns Retry, should re-add step to queue (simple retry)")
                void execute_WhenReflectionRetry_ReAddsStepToQueue() {
                        // Arrange - Hackathon: repair step injection removed for IP protection
                        // Retry now simply re-adds the original step to the end of the queue
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep))
                                        .thenReturn(Optional.empty());

                        when(reflector.reflect(any(ActionStep.class), any(), any(), isNull(), eq(0)))
                                        .thenReturn(ReflectionResult.retry("Element not found", List.of()));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert - step is pushed back to queue (simple retry, no LPUSH pattern)
                        verify(actionQueue).push(eq(testRun.getId()), any(ActionStep.class));
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When reflection returns Retry without repair steps, should retry original step")
                void execute_WhenReflectionRetrySimple_RetriesOriginalStep() {
                        // Arrange
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep))
                                        .thenReturn(Optional.empty());

                        when(reflector.reflect(any(ActionStep.class), any(), any(), isNull(), eq(0)))
                                        .thenReturn(ReflectionResult.retry("Retrying", List.of()));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(actionQueue).push(testRun.getId(), ActionStepFactory.withSelector(clickStep, "button#login"));
                }

                @Test
                @DisplayName("When reflection returns Wait, should sleep and retry step")
                void execute_WhenReflectionWait_SleepsAndRetries() {
                        // Arrange
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep))
                                        .thenReturn(Optional.empty());

                        when(reflector.reflect(any(ActionStep.class), any(), any(), isNull(), eq(0)))
                                        .thenReturn(ReflectionResult.waitFor("DOM updating", 1000));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(sleeper).sleep(1000);
                        verify(actionQueue).push(testRun.getId(), ActionStepFactory.withSelector(clickStep, "button#login"));
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When reflection returns Abort, should fail run")
                void execute_WhenReflectionAbort_FailsRun() {
                        // Arrange
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep))
                                        .thenReturn(Optional.empty());

                        when(reflector.reflect(any(ActionStep.class), any(), any(), isNull(), eq(0)))
                                        .thenReturn(ReflectionResult.abort("Max retries exceeded"));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                        reason -> assertThat(reason).contains("Max retries exceeded"));
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When queue is empty, should complete run")
                void execute_WhenQueueEmpty_CompletesRun() {
                        // Arrange
                        when(actionQueue.pop(testRun.getId())).thenReturn(Optional.empty());

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
                        verify(testRunRepository, atLeastOnce()).save(testRun);
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When step execution throws exception, should fail run and cleanup")
                void execute_WhenStepThrows_FailsRunAndCleansUp() {
                        // Arrange
                        lenient().when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(clickStep));

                        lenient().when(browserDriver.callTool(eq("click"), any()))
                                        .thenThrow(new RuntimeException("Browser crashed"));

                        // Reflector will be called with error message
                        lenient().when(reflector.reflect(any(ActionStep.class), any(), isNull(), anyString(), eq(0)))
                                        .thenReturn(ReflectionResult.abort("Browser crashed"));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        verify(browserDriver).closeContext();
                }
        }

        // ========== CLEANUP (FINALLY BLOCK) ==========

        @Nested
        @DisplayName("Cleanup (Finally Block)")
        class CleanupBehavior {

                @BeforeEach
                void setUpPassingPreFlight() {
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);
                }

                @Test
                @DisplayName("Cleanup should close context normally")
                void cleanup_Normally_ClosesContext() {
                        // Arrange
                        List<ActionStep> plan = List.of(ActionStepFactory.navigate(TARGET_URL));
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(plan);
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);
                        lenient().when(actionQueue.pop(any())).thenReturn(Optional.empty());

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver).closeContext();
                        verify(browserDriver, never()).forceRestart();
                }

                @Test
                @DisplayName("When closeContext throws, should force restart")
                void cleanup_WhenCloseContextThrows_ForceRestarts() {
                        // Arrange
                        List<ActionStep> plan = List.of(ActionStepFactory.navigate(TARGET_URL));
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(plan);
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);
                        lenient().when(actionQueue.pop(any())).thenReturn(Optional.empty());

                        doThrow(new RuntimeException("Context close failed"))
                                        .when(browserDriver).closeContext();

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver).closeContext();
                        verify(browserDriver).forceRestart();
                }

                @Test
                @DisplayName("Cleanup runs even when execution crashes")
                void execute_WhenExecutionCrashes_CleanupStillRuns() {
                        // Arrange
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class)))
                                        .thenThrow(new RuntimeException("Planning engine failed"));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                        reason -> assertThat(reason).contains("SYSTEM_ERROR: Internal Engine Error"));

                        // Cleanup MUST still run
                        verify(browserDriver).closeContext();
                }
        }

        // ========== ACTION TOOL MAPPING ==========

        @Nested
        @DisplayName("Action to Tool Mapping")
        class ActionToolMapping {

                @BeforeEach
                void setUpForToolCall() {
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);
                        when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        when(planSanitizer.isSafe(any(), any())).thenReturn(true);
                        lenient().when(browserDriver.callTool(anyString(), any()))
                                        .thenReturn(Map.of());
                }

                @Test
                @DisplayName("Navigate action should map to navigate_page tool")
                void navigateAction_MapsToNavigatePageTool() {
                        // Arrange
                        ActionStep navigateStep = ActionStepFactory.navigate("https://example.com/login");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(navigateStep));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(navigateStep))
                                        .thenReturn(Optional.empty());
                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver).callTool(eq("navigate_page"),
                                        argThat(params -> params.containsKey("url") &&
                                                        params.get("url").equals("https://example.com/login")));
                }

                @Test
                @DisplayName("Type action should map to fill tool")
                void typeAction_MapsToFillTool() {
                        // Arrange
                        ActionStep typeStep = ActionStepFactory.type("email input", "test@example.com");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(typeStep));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(typeStep))
                                        .thenReturn(Optional.empty());
                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver).callTool(eq("fill"), any());
                }
        }

        // ========== SNAPSHOT PARSING ==========

        @Nested
        @DisplayName("Snapshot Parsing")
        class SnapshotParsing {

                @BeforeEach
                void setUpForSnapshotTests() {
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);
                        when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        when(planSanitizer.isSafe(any(), any())).thenReturn(true);
                }

                @Test
                @DisplayName("Should parse valid MCP snapshot response with content, url, title")
                void getSnapshot_WithValidResponse_ParsesAllFields() {
                        // Arrange
                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(step))
                                        .thenReturn(Optional.empty());

                        // Mock MCP response format: { "content": [ { "type": "text", "text": "..." } ]
                        // }
                        String jsonPayload = "{\"content\":\"[e0] button \\\"Login\\\"\",\"url\":\"https://example.com\",\"title\":\"Login Page\"}";
                        Map<String, Object> mcpResponse = Map.of(
                                        "content", List.of(Map.of("type", "text", "text", jsonPayload)));
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(mcpResponse);

                        // Capture the snapshot passed to reflector
                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert - verify snapshot was taken
                        verify(browserDriver, atLeast(1)).callTool(eq("take_snapshot"), any());
                }

                @Test
                @DisplayName("Should handle empty content list gracefully")
                void getSnapshot_WithEmptyContentList_ReturnsEmptySnapshot() {
                        // Arrange
                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(step))
                                        .thenReturn(Optional.empty());

                        // Empty content list
                        Map<String, Object> mcpResponse = Map.of("content", List.of());
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(mcpResponse);

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act - should not throw
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver, atLeast(1)).callTool(eq("take_snapshot"), any());
                }

                @Test
                @DisplayName("Should handle missing text field gracefully")
                void getSnapshot_WithMissingTextField_ReturnsEmptySnapshot() {
                        // Arrange
                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(step))
                                        .thenReturn(Optional.empty());

                        // Content without text field
                        Map<String, Object> mcpResponse = Map.of(
                                        "content", List.of(Map.of("type", "text")));
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(mcpResponse);

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act - should not throw
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver, atLeast(1)).callTool(eq("take_snapshot"), any());
                }

                @Test
                @DisplayName("Should handle escaped characters in JSON content")
                void getSnapshot_WithEscapedCharacters_ParsesCorrectly() {
                        // Arrange
                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(step))
                                        .thenReturn(Optional.empty());

                        // JSON with escaped newlines, tabs, and quotes
                        String jsonPayload = "{\"content\":\"Line1\\nLine2\\tTabbed\\\"Quoted\\\"\",\"url\":\"https://example.com\",\"title\":\"Test\"}";
                        Map<String, Object> mcpResponse = Map.of(
                                        "content", List.of(Map.of("type", "text", "text", jsonPayload)));
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(mcpResponse);

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act - should parse escaped chars correctly
                        orchestrator.execute(testRun);

                        // Assert
                        verify(browserDriver, atLeast(1)).callTool(eq("take_snapshot"), any());
                }

                @Test
                @DisplayName("Should handle exception from browser driver gracefully")
                void getSnapshot_WhenBrowserDriverThrows_ReturnsEmptySnapshot() {
                        // Arrange
                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(actionQueue.pop(testRun.getId()))
                                        .thenReturn(Optional.of(step))
                                        .thenReturn(Optional.empty());

                        // First call for action, second for before snapshot throws
                        when(browserDriver.callTool(eq("take_snapshot"), any()))
                                        .thenThrow(new RuntimeException("Browser crashed"));
                        when(browserDriver.callTool(eq("click"), any()))
                                        .thenReturn(Map.of());

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                        .thenReturn(ReflectionResult.success(null));

                        // Act - should handle gracefully
                        orchestrator.execute(testRun);

                        // Assert - should still complete (with empty snapshots)
                        verify(browserDriver, atLeast(1)).callTool(eq("take_snapshot"), any());
                }
        }

        // ========== PERFORMANCE METRICS PARSING ==========

        @Nested
        @DisplayName("Performance Metrics Parsing")
        class PerformanceMetricsParsing {

                @BeforeEach
                void setUpForPerfTests() {
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);
                        when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        when(planSanitizer.isSafe(any(), any())).thenReturn(true);
                }

                @Test
                @DisplayName("Should extract performance metrics from MCP content format")
                void measurePerformance_WithMcpFormat_ExtractsMetrics() {
                        // Arrange - create a measure_performance step
                        ActionStep perfStep = new ActionStep(
                                "step-1", "measure_performance", "check vitals",
                                Optional.empty(), Optional.empty(), Map.of()
                        );
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(perfStep));
                        when(actionQueue.pop(testRun.getId()))
                                .thenReturn(Optional.of(perfStep))
                                .thenReturn(Optional.empty());

                        // Mock MCP response format - this is the key test!
                        // The actual tool result is JSON inside the text field
                        String perfJsonPayload = "{\"success\":true,\"webVitals\":{\"lcp\":2500.0,\"cls\":0.05,\"ttfb\":800.0},\"navigation\":{\"pageLoad\":3500.0},\"totalResources\":100,\"totalTransferSizeKb\":500}";
                        Map<String, Object> mcpPerfResponse = Map.of(
                                "content", List.of(Map.of("type", "text", "text", perfJsonPayload))
                        );
                        when(browserDriver.callTool(eq("get_performance_metrics"), any())).thenReturn(mcpPerfResponse);

                        // Mock snapshot response
                        String snapshotJson = "{\"content\":\"page content\",\"url\":\"https://example.com\",\"title\":\"Test\"}";
                        Map<String, Object> mcpSnapshotResponse = Map.of(
                                "content", List.of(Map.of("type", "text", "text", snapshotJson))
                        );
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(mcpSnapshotResponse);

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert - verify the test run was saved with performance metrics
                        verify(testRunRepository, atLeast(1)).save(argThat(run -> {
                                List<ExecutedStep> steps = run.getExecutedSteps();
                                if (steps.isEmpty()) return false;
                                ExecutedStep step = steps.get(0);
                                return step.hasPerformanceMetrics() &&
                                       step.performanceMetrics() != null &&
                                       step.performanceMetrics().getLcp().isPresent() &&
                                       step.performanceMetrics().getLcp().get() == 2500.0;
                        }));
                }

                @Test
                @DisplayName("Should handle MCP error response gracefully")
                void measurePerformance_WithErrorResponse_ReturnsNullMetrics() {
                        // Arrange
                        ActionStep perfStep = new ActionStep(
                                "step-1", "measure_performance", "check vitals",
                                Optional.empty(), Optional.empty(), Map.of()
                        );
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(perfStep));
                        when(actionQueue.pop(testRun.getId()))
                                .thenReturn(Optional.of(perfStep))
                                .thenReturn(Optional.empty());

                        // Mock error response
                        String errorJson = "{\"success\":false,\"error\":\"Browser context not ready\"}";
                        Map<String, Object> mcpErrorResponse = Map.of(
                                "content", List.of(Map.of("type", "text", "text", errorJson))
                        );
                        when(browserDriver.callTool(eq("get_performance_metrics"), any())).thenReturn(mcpErrorResponse);

                        // Mock snapshot
                        String snapshotJson = "{\"content\":\"\",\"url\":\"\",\"title\":\"\"}";
                        when(browserDriver.callTool(eq("take_snapshot"), any())).thenReturn(
                                Map.of("content", List.of(Map.of("type", "text", "text", snapshotJson)))
                        );

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert - step should complete but without metrics
                        verify(testRunRepository, atLeast(1)).save(argThat(run -> {
                                List<ExecutedStep> steps = run.getExecutedSteps();
                                if (steps.isEmpty()) return false;
                                ExecutedStep step = steps.get(0);
                                // Should NOT have metrics due to error
                                return !step.hasPerformanceMetrics();
                        }));
                }
        }

        // ========== TIMEOUT PROTECTION ==========

        @Nested
        @DisplayName("Timeout Protection")
        class TimeoutProtection {

                @Test
                @DisplayName("When test exceeds timeout duration, should fail with timeout error")
                void execute_WhenTimeoutExceeded_FailsWithTimeoutError() {
                        // Arrange - Use a config with very short timeout (1 minute)
                        TestOrchestratorConfig shortTimeoutConfig = new TestOrchestratorConfig()
                                .withTestTimeoutMinutes(1);

                        // Create a clock that advances time on each call
                        // The new flow checks timeout after context creation and after planning
                        Clock mockClock = mock(Clock.class);
                        when(mockClock.instant())
                                .thenReturn(NOW)                    // 1. overallStartTime in execute()
                                .thenReturn(NOW)                    // 2. checkOverallTimeout after context creation
                                .thenReturn(NOW.plusSeconds(120));  // 3. checkOverallTimeout after planning (2 min > 1 min timeout)

                        AgentOrchestrator timeoutOrchestrator = new AgentOrchestrator(
                                stepPlanner,
                                browserDriver,
                                actionQueue,
                                doneQueue,
                                testRunRepository,
                                smartDriver,
                                artifactStorage,
                                promptInjectionDetector,
                                planSanitizer,
                                reflector,
                                obstacleDetector,
                                suggestionService,
                                shortTimeoutConfig,
                                mockClock,
                                sleeper,
                                eventPublisher);

                        // Setup pre-flight and planning to pass
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);

                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        // Use lenient() for stubs that may not be called due to early timeout
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                        // Act
                        timeoutOrchestrator.execute(testRun);

                        // Assert - timeout should trigger after planning phase
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                reason -> {
                                        assertThat(reason).contains("TIMEOUT");
                                        assertThat(reason).contains("planning phase");
                                });

                        // Cleanup should still run
                        verify(browserDriver).closeContext();
                }

                @Test
                @DisplayName("When test completes within timeout, should succeed")
                void execute_WhenWithinTimeout_Succeeds() {
                        // Arrange - Use default config with 30 minute timeout
                        // Clock always returns NOW (no time passes)
                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);

                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                        // Queue empties after one iteration (test completes quickly)
                        when(actionQueue.pop(testRun.getId()))
                                .thenReturn(Optional.of(step))
                                .thenReturn(Optional.empty());

                        when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                                .thenReturn(ReflectionResult.success(null));

                        // Act
                        orchestrator.execute(testRun);

                        // Assert - should complete successfully, not timeout
                        assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
                        assertThat(testRun.getFailureReason()).isEmpty();
                }

                @Test
                @DisplayName("Timeout message should include elapsed time details")
                void execute_WhenTimeout_MessageIncludesElapsedTime() {
                        // Arrange
                        TestOrchestratorConfig shortTimeoutConfig = new TestOrchestratorConfig()
                                .withTestTimeoutMinutes(5);

                        // New flow checks timeout after context creation and after planning
                        Clock mockClock = mock(Clock.class);
                        when(mockClock.instant())
                                .thenReturn(NOW)                    // 1. overallStartTime in execute()
                                .thenReturn(NOW)                    // 2. checkOverallTimeout after context creation
                                .thenReturn(NOW.plusSeconds(360));  // 3. checkOverallTimeout after planning (6 min > 5 min timeout)

                        AgentOrchestrator timeoutOrchestrator = new AgentOrchestrator(
                                stepPlanner,
                                browserDriver,
                                actionQueue,
                                doneQueue,
                                testRunRepository,
                                smartDriver,
                                artifactStorage,
                                promptInjectionDetector,
                                planSanitizer,
                                reflector,
                                obstacleDetector,
                                suggestionService,
                                shortTimeoutConfig,
                                mockClock,
                                sleeper,
                                eventPublisher);

                        when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                        when(browserDriver.isRunning()).thenReturn(true);

                        ActionStep step = ActionStepFactory.click("button");
                        when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                        // Use lenient() for stubs that may not be called due to early timeout
                        lenient().when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                        lenient().when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                        // Act
                        timeoutOrchestrator.execute(testRun);

                        // Assert - verify elapsed time is in the message
                        assertThat(testRun.getFailureReason()).hasValueSatisfying(
                                reason -> {
                                        assertThat(reason).contains("5 minutes"); // configured timeout
                                        assertThat(reason).contains("6 min");     // elapsed time
                                });
                }
        }

        // ========== CONSOLE LOG CAPTURE ==========

        @Nested
        @DisplayName("Console Log Capture")
        class ConsoleLogCapture {

            @BeforeEach
            void setUpForConsoleTests() {
                when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                when(browserDriver.isRunning()).thenReturn(true);
                when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                when(planSanitizer.isSafe(any(), any())).thenReturn(true);
            }

            @Test
            @DisplayName("Should capture console logs and pass them to ExecutedStep")
            void execute_CapturesConsoleLogs() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                Map<String, Object> toolResponse = Map.of(
                        "content", List.of(),
                        "logs", Map.of(
                                "console", List.of("[JS Console] Error 1"),
                                "pageErrors", List.of("[Uncaught Exception] Error 2")
                        )
                );
                when(browserDriver.callTool(eq("click"), any())).thenReturn(toolResponse);

                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert
                verify(doneQueue).record(eq(testRun.getId()), argThat(executedStep ->
                        executedStep.hasConsoleErrors() &&
                        executedStep.consoleErrorsSummary().contains("Error 1") &&
                        executedStep.consoleErrorsSummary().contains("Error 2")
                ));
            }
        }

        // ========== OBSTACLE DETECTION AND DISMISSAL ==========

        @Nested
        @DisplayName("Obstacle Detection and Dismissal")
        class ObstacleDetection {

            private DomSnapshot snapshotWithObstacle;
            private DomSnapshot snapshotWithoutObstacle;
            private ObstacleDetector.ObstacleInfo cookieObstacle;

            @BeforeEach
            void setUpForObstacleTests() {
                when(promptInjectionDetector.areSafe(anyList())).thenReturn(true);
                when(browserDriver.isRunning()).thenReturn(true);
                when(planSanitizer.sanitize(anyList(), anyString())).thenAnswer(inv -> inv.getArgument(0));
                when(planSanitizer.isSafe(any(), any())).thenReturn(true);

                snapshotWithObstacle = new DomSnapshot("<div class='cookie-banner'>Accept</div>", "url", "title", NOW);
                snapshotWithoutObstacle = new DomSnapshot("<div>Clean page</div>", "url", "title", NOW);

                cookieObstacle = new ObstacleDetector.ObstacleInfo(
                        "cookie_consent",
                        "Cookie consent banner",
                        "#accept-cookies",
                        "Accept All",
                        ObstacleDetector.Confidence.HIGH
                );

                // Default snapshot response
                String snapshotPayload = "{\"content\":\"snapshot\",\"url\":\"url\",\"title\":\"title\"}";
                lenient().when(browserDriver.callTool(eq("take_snapshot"), any()))
                        .thenReturn(Map.of(
                                "content",
                                List.of(Map.of("type", "text", "text", snapshotPayload))));
                lenient().when(browserDriver.callTool(eq("take_screenshot"), any()))
                        .thenReturn(Map.of(
                                "content",
                                List.of(Map.of(
                                        "type", "image",
                                        "data", "ZGF0YQ=="))));
            }

            @Test
            @DisplayName("When obstacle detected and dismissed successfully, should verify it's gone before marking as dismissed")
            void clearObstacles_WhenDismissedSuccessfully_VerifiesGone() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // First detection returns obstacle, second returns empty (verified dismissed)
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // First call: obstacle detected
                        .thenReturn(Optional.empty())             // Second call: obstacle gone (verified)
                        .thenReturn(Optional.empty());            // Subsequent calls: no obstacle

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - obstacle detection called at least twice (detect + verify)
                verify(obstacleDetector, atLeast(2)).detect(any());
                // Click dismiss called exactly once for the cookie consent
                verify(browserDriver, atLeast(1)).callTool(eq("click"), argThat(params ->
                        params.containsKey("selector") && "#accept-cookies".equals(params.get("selector"))));
            }

            @Test
            @DisplayName("When obstacle persists after click, should retry with JS click before giving up")
            void clearObstacles_WhenClickFails_RetriesWithJsClickBeforeGivingUp() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // Obstacle persists after clicks (simulating failed dismissal)
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // 1st: obstacle detected
                        .thenReturn(Optional.of(cookieObstacle))  // 2nd: still there after native click
                        .thenReturn(Optional.of(cookieObstacle))  // 3rd: still there after JS click (give up)
                        .thenReturn(Optional.empty());            // Later calls

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(browserDriver.callTool(eq("evaluate"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should try native click first, then JS click on retry
                verify(browserDriver, times(1)).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
                verify(browserDriver, times(1)).callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && params.get("script").toString().contains("#accept-cookies")));
            }

            @Test
            @DisplayName("When obstacle was already dismissed in previous step, should skip without retry")
            void clearObstacles_WhenAlreadyDismissedInPreviousStep_SkipsWithoutRetry() {
                // Arrange
                ActionStep step1 = ActionStepFactory.click("button1");
                ActionStep step2 = ActionStepFactory.click("button2");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step1, step2));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step1))
                        .thenReturn(Optional.of(step2))
                        .thenReturn(Optional.empty());

                // Step 1: obstacle detected and dismissed
                // Step 2: same obstacle type detected again (should be skipped)
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // Step 1: detect
                        .thenReturn(Optional.empty())             // Step 1: verified gone
                        .thenReturn(Optional.of(cookieObstacle))  // Step 2: same type detected again
                        .thenReturn(Optional.empty());            // Later calls

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should only click dismiss once (step 1), not again in step 2
                verify(browserDriver, times(1)).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
            }

            @Test
            @DisplayName("When multiple different obstacle types exist, should handle each separately")
            void clearObstacles_WithMultipleTypes_HandlesEachSeparately() {
                // Arrange
                ObstacleDetector.ObstacleInfo legalObstacle = new ObstacleDetector.ObstacleInfo(
                        "legal_agreement",
                        "Terms of Service popup",
                        "#agree-tos",
                        "I Agree",
                        ObstacleDetector.Confidence.HIGH
                );

                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // First cookie consent, then legal agreement
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // 1st: cookie consent
                        .thenReturn(Optional.of(legalObstacle))   // 2nd: legal (after cookie dismissed)
                        .thenReturn(Optional.empty())             // 3rd: all clear
                        .thenReturn(Optional.empty());

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should click both dismiss buttons
                verify(browserDriver).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
                verify(browserDriver).callTool(eq("click"), argThat(params ->
                        "#agree-tos".equals(params.get("selector"))));
            }

            @Test
            @DisplayName("When native click throws exception, should retry with JS click")
            void clearObstacles_WhenNativeClickThrows_RetriesWithJsClick() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // Obstacle persists
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // 1st: detected
                        .thenReturn(Optional.of(cookieObstacle))  // 2nd: still there after native click threw
                        .thenReturn(Optional.of(cookieObstacle))  // 3rd: still there after JS click (give up)
                        .thenReturn(Optional.empty());

                // Native click throws exception
                when(browserDriver.callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector")))))
                        .thenThrow(new RuntimeException("Element not clickable"));

                // JS click succeeds but obstacle persists
                when(browserDriver.callTool(eq("evaluate"), any())).thenReturn(Map.of());

                // Allow other clicks to succeed
                lenient().when(browserDriver.callTool(eq("click"), argThat(params ->
                        params.get("selector") != null && !"#accept-cookies".equals(params.get("selector")))))
                        .thenReturn(Map.of());

                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should attempt native click first (throws), then JS click on retry
                verify(browserDriver, times(1)).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
                verify(browserDriver, times(1)).callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && params.get("script").toString().contains("#accept-cookies")));
            }

            @Test
            @DisplayName("When no obstacle detected initially, should not attempt any dismissal")
            void clearObstacles_WhenNoObstacle_DoesNothing() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // No obstacles ever
                when(obstacleDetector.detect(any())).thenReturn(Optional.empty());

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - no dismiss clicks (only the actual step click)
                verify(browserDriver, times(1)).callTool(eq("click"), any());
            }

            @Test
            @DisplayName("Low confidence obstacle should be skipped after first retry attempt")
            void clearObstacles_LowConfidence_SkippedAfterFirstAttempt() {
                // Arrange
                ObstacleDetector.ObstacleInfo lowConfidenceObstacle = new ObstacleDetector.ObstacleInfo(
                        "possible_popup",
                        "Might be a popup",
                        "#maybe-close",
                        "Close",
                        ObstacleDetector.Confidence.LOW
                );

                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // Low confidence obstacle persists
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(lowConfidenceObstacle))  // 1st: detected
                        .thenReturn(Optional.of(lowConfidenceObstacle))  // 2nd: still there (low conf skipped)
                        .thenReturn(Optional.empty());

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should only attempt once for low confidence
                verify(browserDriver, times(1)).callTool(eq("click"), argThat(params ->
                        "#maybe-close".equals(params.get("selector"))));
            }

            @Test
            @DisplayName("Should wait before clicking to let animations complete (pre-click delay)")
            void clearObstacles_ShouldWaitBeforeClick() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // Obstacle dismissed after first click
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))
                        .thenReturn(Optional.empty())
                        .thenReturn(Optional.empty());

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should have pre-click delay (250ms) followed by post-click wait (500ms)
                InOrder inOrder = inOrder(sleeper, browserDriver);
                inOrder.verify(sleeper).sleep(250);  // Pre-click delay
                inOrder.verify(browserDriver).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
                inOrder.verify(sleeper).sleep(500);  // Post-click wait
            }

            @Test
            @DisplayName("JS click should use document.querySelector with proper selector escaping")
            void clearObstacles_JsClick_ProperlyEscapesSelector() {
                // Arrange
                ObstacleDetector.ObstacleInfo obstacleWithQuotes = new ObstacleDetector.ObstacleInfo(
                        "cookie_consent",
                        "Cookie consent",
                        "button[data-test='accept']",  // Selector with single quotes
                        "Accept",
                        ObstacleDetector.Confidence.HIGH
                );

                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // Obstacle persists after first click, requiring JS click
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(obstacleWithQuotes))
                        .thenReturn(Optional.of(obstacleWithQuotes))
                        .thenReturn(Optional.empty());

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(browserDriver.callTool(eq("evaluate"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - JS click should escape single quotes in selector
                verify(browserDriver).callTool(eq("evaluate"), argThat(params -> {
                    String script = params.get("script").toString();
                    // Should escape single quotes: 'accept' becomes \'accept\'
                    return script.contains("button[data-test=\\'accept\\']") && script.contains(".click()");
                }));
            }

            @Test
            @DisplayName("When AI detection fails, should try fallback consent selectors using JS evaluate")
            void clearObstacles_WhenAiDetectionFails_TriesFallbackSelectors() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // AI detection always returns empty (fails to detect obstacle)
                when(obstacleDetector.detect(any())).thenReturn(Optional.empty());

                // Fallback uses JS evaluate to click (not the click tool)
                // Mock evaluate to return "not found" by default
                lenient().when(browserDriver.callTool(eq("evaluate"), any()))
                        .thenReturn(Map.of("content", List.of(Map.of("type", "text", "text", "not found"))));

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should try fallback selectors using evaluate (JS click)
                verify(browserDriver, atLeastOnce()).callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && params.get("script").toString().contains("onetrust")));
            }

            @Test
            @DisplayName("Fallback selectors should only be tried once per step")
            void clearObstacles_FallbackSelectors_OnlyTriedOncePerStep() {
                // Arrange
                ActionStep step1 = ActionStepFactory.click("button1");
                ActionStep step2 = ActionStepFactory.click("button2");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step1, step2));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step1))
                        .thenReturn(Optional.of(step2))
                        .thenReturn(Optional.empty());

                // AI detection always returns empty
                when(obstacleDetector.detect(any())).thenReturn(Optional.empty());

                // Fallback uses JS evaluate - return "not found" so it tries all selectors
                lenient().when(browserDriver.callTool(eq("evaluate"), any()))
                        .thenReturn(Map.of("content", List.of(Map.of("type", "text", "text", "not found"))));

                // All regular clicks succeed
                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());

                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - fallback selectors (evaluate calls) should have reasonable upper bound
                // Each step tries all ~20 selectors once = ~40 evaluate calls for 2 steps
                verify(browserDriver, atMost(50)).callTool(eq("evaluate"), any());
            }

            @Test
            @DisplayName("Fallback selectors are tried once per test run even if AI dismissed obstacles")
            void clearObstacles_FallbackTriedOnceEvenAfterAiSuccess() {
                // Arrange - This tests the correct behavior: fallback selectors are always tried
                // once per test run because there might be iframe consent dialogs AI can't see
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // AI detection finds obstacle and it gets dismissed
                when(obstacleDetector.detect(any()))
                        .thenReturn(Optional.of(cookieObstacle))  // First call: obstacle detected
                        .thenReturn(Optional.empty())             // Second call: obstacle gone -> fallback tried
                        .thenReturn(Optional.empty());            // Later calls

                // Fallback selectors return "not found"
                lenient().when(browserDriver.callTool(eq("evaluate"), any()))
                        .thenReturn(Map.of("content", List.of(Map.of("type", "text", "text", "not found"))));
                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - AI-detected selector clicked via native click
                verify(browserDriver, times(1)).callTool(eq("click"), argThat(params ->
                        "#accept-cookies".equals(params.get("selector"))));
                // Fallback selectors are tried once (to catch potential iframe consent dialogs)
                verify(browserDriver, atLeastOnce()).callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && params.get("script").toString().contains("onetrust")));
            }

            @Test
            @DisplayName("When fallback selector throws exception, should continue to next selector")
            void clearObstacles_WhenFallbackThrows_ContinuesToNext() {
                // Arrange
                ActionStep step = ActionStepFactory.click("button");
                when(stepPlanner.createPlan(any(), any(), any(TestPersona.class))).thenReturn(List.of(step));
                when(actionQueue.pop(testRun.getId()))
                        .thenReturn(Optional.of(step))
                        .thenReturn(Optional.empty());

                // AI detection fails
                when(obstacleDetector.detect(any())).thenReturn(Optional.empty());

                // Fallback uses JS evaluate - first throws, subsequent return "not found"
                when(browserDriver.callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && params.get("script").toString().contains("onetrust-accept-btn-handler"))))
                        .thenThrow(new RuntimeException("Element not found"));

                lenient().when(browserDriver.callTool(eq("evaluate"), argThat(params ->
                        params.get("script") != null && !params.get("script").toString().contains("onetrust-accept-btn-handler"))))
                        .thenReturn(Map.of("content", List.of(Map.of("type", "text", "text", "not found"))));

                when(browserDriver.callTool(eq("click"), any())).thenReturn(Map.of());
                when(reflector.reflect(any(), any(), any(), any(), anyInt()))
                        .thenReturn(ReflectionResult.success(null));

                // Act
                orchestrator.execute(testRun);

                // Assert - should have tried more than one fallback selector (via evaluate)
                verify(browserDriver, atLeast(2)).callTool(eq("evaluate"), any());
            }
        }
}
