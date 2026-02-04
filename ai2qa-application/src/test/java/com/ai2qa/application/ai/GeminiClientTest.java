package com.ai2qa.application.ai;

import com.ai2qa.application.metrics.AiMetricsService;
import com.ai2qa.application.port.AiProviderInfo;
import com.ai2qa.application.port.AiResponse;
import com.ai2qa.application.port.BrowserModeProvider;
import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.security.PromptSanitizer;
import com.ai2qa.domain.context.TenantContext;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.TestPersona;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("GeminiClient")
class GeminiClientTest {

    // Hackathon: TenantContext always returns "hackathon-demo"
    private static final String TENANT_ID = "hackathon-demo";
    private static final String MODEL_PROVIDER = "vertex-ai";
    private static final String MODEL_NAME = "gemini-2.0-flash";

    private ChatClientPort plannerClient;
    private ChatClientPort selectorClient;
    private ChatClientPort repairClient;
    private AiMetricsService metricsService;
    private BrowserModeProvider browserModeProvider;
    private AiProviderInfo providerInfo;
    private PromptSanitizer promptSanitizer;
    private GeminiClient geminiClient;

    @BeforeEach
    void setUp() {
        plannerClient = mock(ChatClientPort.class);
        selectorClient = mock(ChatClientPort.class);
        repairClient = mock(ChatClientPort.class);
        metricsService = mock(AiMetricsService.class);
        browserModeProvider = mock(BrowserModeProvider.class);
        providerInfo = new AiProviderInfo(MODEL_PROVIDER, MODEL_NAME);
        promptSanitizer = new PromptSanitizer();  // Use real instance for integration testing

        when(browserModeProvider.getEngine()).thenReturn("puppeteer");
        when(browserModeProvider.isAriaEffectivelyEnabled()).thenReturn(true);

        geminiClient = new GeminiClient(
                plannerClient,
                selectorClient,
                repairClient,
                new ObjectMapper(),
                browserModeProvider,
                metricsService,
                providerInfo,
                promptSanitizer
        );

        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("planGoal")
    class PlanGoal {

        @Test
        @DisplayName("records plan generation metric on success")
        void recordsPlanGenerationMetricOnSuccess() {
            AiResponse response = new AiResponse(
                    "[]", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, null);

            verify(metricsService).recordPlanGeneration(
                    eq(TENANT_ID),
                    isNull(),
                    eq(MODEL_PROVIDER),
                    eq(MODEL_NAME),
                    eq(100),
                    eq(50),
                    eq(200),
                    eq(true),
                    isNull()
            );
        }

        @Test
        @DisplayName("returns fallback plan on exception without recording metric")
        void returnsFallbackPlanOnException() {
            when(plannerClient.callWithMetrics(any(), any(), any()))
                    .thenThrow(new RuntimeException("API error"));

            var steps = geminiClient.planGoal("Login", "https://example.com", TestPersona.STANDARD, null);

            assertFalse(steps.isEmpty());
            verify(metricsService, never()).recordPlanGeneration(
                    any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), any());
        }

        @Test
        @DisplayName("includes memory context in system prompt when provided")
        void includesMemoryContextInSystemPrompt() {
            AiResponse response = new AiResponse("[]", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            String memoryContext = "\n\n[GLOBAL HIVE MIND ACTIVATED]\n- [framework:react]: Use data-testid for stable selectors\n";

            geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, memoryContext);

            ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(plannerClient).callWithMetrics(systemPromptCaptor.capture(), any(), any());

            String actualSystemPrompt = systemPromptCaptor.getValue();
            assertTrue(actualSystemPrompt.contains("[GLOBAL HIVE MIND ACTIVATED]"));
            assertTrue(actualSystemPrompt.contains("framework:react"));
            assertTrue(actualSystemPrompt.contains("data-testid"));
        }

        @Test
        @DisplayName("uses only persona prompt when memory context is null")
        void usesOnlyPersonaPromptWhenMemoryContextNull() {
            AiResponse response = new AiResponse("[]", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, null);

            ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(plannerClient).callWithMetrics(systemPromptCaptor.capture(), any(), any());

            String actualSystemPrompt = systemPromptCaptor.getValue();
            assertEquals(TestPersona.STANDARD.getSystemPrompt(), actualSystemPrompt);
        }

        @Test
        @DisplayName("uses only persona prompt when memory context is blank")
        void usesOnlyPersonaPromptWhenMemoryContextBlank() {
            AiResponse response = new AiResponse("[]", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, "   ");

            ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(plannerClient).callWithMetrics(systemPromptCaptor.capture(), any(), any());

            String actualSystemPrompt = systemPromptCaptor.getValue();
            assertEquals(TestPersona.STANDARD.getSystemPrompt(), actualSystemPrompt);
        }

        @Test
        @DisplayName("parses multi-step JSON array response correctly")
        void parsesMultiStepJsonArrayResponse() {
            // This is the correct format that matches ARCHITECT_JSON_SCHEMA
            String multiStepResponse = """
                [
                  {"action": "click", "target": "login button"},
                  {"action": "type", "target": "email input", "value": "test@example.com"},
                  {"action": "type", "target": "password input", "value": "secret123"},
                  {"action": "click", "target": "submit button"},
                  {"action": "wait", "target": "dashboard to load", "params": {"ms": 2000}},
                  {"action": "screenshot", "target": "verify login success"}
                ]
                """;

            AiResponse response = new AiResponse(multiStepResponse, 200, 100, 300, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, null);

            assertEquals(6, steps.size(), "Should parse all 6 steps from JSON array");
            assertEquals("click", steps.get(0).action());
            assertEquals("login button", steps.get(0).target());
            assertEquals("type", steps.get(1).action());
            assertEquals("test@example.com", steps.get(1).value().orElse(null));
            assertEquals("screenshot", steps.get(5).action());
        }

        @Test
        @DisplayName("parses markdown-wrapped JSON array response")
        void parsesMarkdownWrappedJsonArrayResponse() {
            // Note: navigate steps are filtered out (StepPlanner adds initial navigate)
            String markdownWrapped = """
                ```json
                [
                  {"action": "navigate", "target": "homepage", "value": "https://example.com"},
                  {"action": "click", "target": "search button"},
                  {"action": "screenshot", "target": "verify search visible"}
                ]
                ```
                """;

            AiResponse response = new AiResponse(markdownWrapped, 150, 80, 250, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Search feature", "https://example.com", TestPersona.STANDARD, null);

            // Navigate steps are filtered (redundant - StepPlanner handles navigation)
            assertEquals(2, steps.size(), "Should parse 2 steps after filtering redundant navigate step");
            assertEquals("click", steps.get(0).action());
            assertEquals("screenshot", steps.get(1).action());
        }

        @Test
        @DisplayName("returns fallback plan for nested testPlan format - REGRESSION TEST")
        void returnsFallbackPlanForNestedTestPlanFormat() {
            // This is the OLD INCORRECT format - should trigger fallback plan
            String nestedFormat = """
                {
                  "testPlan": {
                    "title": "Login Test",
                    "steps": [
                      {"stepNumber": 1, "action": "NAVIGATE", "targetElement": "homepage"}
                    ]
                  }
                }
                """;

            AiResponse response = new AiResponse(nestedFormat, 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Login", "https://example.com", TestPersona.STANDARD, null);

            // The nested format cannot be parsed as array, so fallback plan is used
            assertFalse(steps.isEmpty(), "Should return fallback plan when parsing fails");
            // Fallback ONLY includes safe actions (wait, screenshot) - NO click/type with vague targets
            assertTrue(steps.stream().anyMatch(s -> "wait".equals(s.action())),
                    "Fallback plan should include wait step");
            assertTrue(steps.stream().anyMatch(s -> "screenshot".equals(s.action())),
                    "Fallback plan should include screenshot step");
            // Verify NO click or type actions in fallback (they would fail)
            assertFalse(steps.stream().anyMatch(s -> "click".equals(s.action())),
                    "Fallback plan should NOT include click (would fail element finding)");
            assertFalse(steps.stream().anyMatch(s -> "type".equals(s.action())),
                    "Fallback plan should NOT include type (would fail element finding)");
        }

        @Test
        @DisplayName("extracts params from step correctly")
        void extractsParamsFromStepCorrectly() {
            String responseWithParams = """
                [
                  {"action": "wait", "target": "element visible", "params": {"ms": 3000, "selector": ".loading"}}
                ]
                """;

            AiResponse response = new AiResponse(responseWithParams, 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Wait test", "https://example.com", TestPersona.STANDARD, null);

            assertEquals(1, steps.size());
            assertEquals("wait", steps.get(0).action());
            assertEquals("3000", steps.get(0).params().get("ms"));
            assertEquals(".loading", steps.get(0).params().get("selector"));
        }

        @Test
        @DisplayName("returns fallback plan when AI returns empty array")
        void returnsFallbackPlanWhenAiReturnsEmptyArray() {
            // AI might legitimately return empty array for very simple goals
            AiResponse response = new AiResponse("[]", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Click the button", "https://example.com", TestPersona.STANDARD, null);

            // Should use safe fallback plan (only wait/screenshot - no element-dependent actions)
            assertFalse(steps.isEmpty(), "Should return fallback plan for empty AI response");
            assertTrue(steps.stream().anyMatch(s -> "screenshot".equals(s.action())),
                    "Fallback plan should include screenshot");
            assertTrue(steps.stream().anyMatch(s -> "wait".equals(s.action())),
                    "Fallback plan should include wait");
            // Fallback should NOT try to click/type with vague targets
            assertFalse(steps.stream().anyMatch(s -> "click".equals(s.action())),
                    "Fallback should NOT include click (would fail element finding)");
        }

        @Test
        @DisplayName("returns safe fallback plan for any invalid response")
        void returnsSafeFallbackPlanForAnyInvalidResponse() {
            // Test that fallback plan is always safe (no element-dependent actions)
            AiResponse response = new AiResponse("invalid json", 100, 50, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Fill out the contact form", "https://example.com", TestPersona.STANDARD, null);

            assertFalse(steps.isEmpty());
            // Fallback should only have safe actions that always succeed
            assertTrue(steps.stream().allMatch(s ->
                    "wait".equals(s.action()) || "screenshot".equals(s.action()) || "navigate".equals(s.action())),
                    "Fallback plan should only contain safe actions (wait, screenshot, navigate)");
        }

        @Test
        @DisplayName("recovers valid steps from truncated JSON response")
        void recoversValidStepsFromTruncatedJsonResponse() {
            // Simulates AI generating a long response that gets truncated mid-JSON
            // Note: navigate steps are filtered out (StepPlanner adds initial navigate)
            String truncatedJson = """
                [
                  {"action": "navigate", "target": "homepage", "value": "https://example.com"},
                  {"action": "wait", "target": "page load", "params": {"ms": 1000}},
                  {"action": "click", "target": "login button"},
                  {"action": "type", "target": "email input", "value": "test@exam
                """;
            // Note: JSON is truncated mid-string value

            AiResponse response = new AiResponse(truncatedJson, 500, 200, 300, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Login to app", "https://example.com", TestPersona.STANDARD, null);

            // Should recover 2 complete steps (navigate is filtered as redundant)
            assertEquals(2, steps.size(), "Should recover 2 complete steps from truncated JSON (navigate filtered)");
            assertEquals("wait", steps.get(0).action());
            assertEquals("click", steps.get(1).action());
        }

        @Test
        @DisplayName("recovers steps when JSON truncated after complete object with comma")
        void recoversStepsWhenTruncatedAfterCompleteObjectWithComma() {
            // Simulates truncation right after a complete object
            // Note: navigate steps are filtered out (StepPlanner adds initial navigate)
            String truncatedJson = """
                [
                  {"action": "navigate", "target": "homepage", "value": "https://example.com"},
                  {"action": "click", "target": "button"},
                  {"action":
                """;

            AiResponse response = new AiResponse(truncatedJson, 200, 100, 200, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var steps = geminiClient.planGoal("Test recovery", "https://example.com", TestPersona.STANDARD, null);

            // Should recover 1 complete step (navigate is filtered as redundant)
            assertEquals(1, steps.size(), "Should recover 1 complete step (navigate filtered)");
            assertEquals("click", steps.get(0).action());
        }
    }

    @Nested
    @DisplayName("findSelector")
    class FindSelector {

        private DomSnapshot snapshot;

        @BeforeEach
        void setUp() {
            snapshot = DomSnapshot.of("- button \"Login\" [ref=e1]", "https://example.com", "Test Page");
        }

        @Test
        @DisplayName("records element find metric on success")
        void recordsElementFindMetricOnSuccess() {
            AiResponse response = new AiResponse("@e1", 80, 10, 150, MODEL_NAME);
            when(selectorClient.callWithMetrics(any())).thenReturn(response);

            Optional<String> result = geminiClient.findSelector("Login button", snapshot);

            assertTrue(result.isPresent());
            assertEquals("@e1", result.get());

            verify(metricsService).recordElementFind(
                    eq(TENANT_ID),
                    isNull(),
                    eq(MODEL_PROVIDER),
                    eq(MODEL_NAME),
                    eq(80),
                    eq(10),
                    eq(150),
                    eq(false)
            );
        }

        @Test
        @DisplayName("records element find failure when NOT_FOUND")
        void recordsElementFindFailureWhenNotFound() {
            AiResponse response = new AiResponse("NOT_FOUND", 80, 5, 100, MODEL_NAME);
            when(selectorClient.callWithMetrics(any())).thenReturn(response);

            Optional<String> result = geminiClient.findSelector("Missing element", snapshot);

            assertTrue(result.isEmpty());

            verify(metricsService).recordElementFindFailure(
                    eq(TENANT_ID),
                    isNull(),
                    eq(MODEL_PROVIDER),
                    eq(MODEL_NAME),
                    eq(80),
                    eq(5),
                    eq(100),
                    eq(false),
                    eq("NOT_FOUND")
            );
        }

        @Test
        @DisplayName("records element find failure when blank response")
        void recordsElementFindFailureWhenBlank() {
            AiResponse response = new AiResponse("  ", 80, 5, 100, MODEL_NAME);
            when(selectorClient.callWithMetrics(any())).thenReturn(response);

            Optional<String> result = geminiClient.findSelector("Missing element", snapshot);

            assertTrue(result.isEmpty());

            verify(metricsService).recordElementFindFailure(
                    any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), eq("NOT_FOUND"));
        }

        @Test
        @DisplayName("returns empty on exception without recording metric")
        void returnsEmptyOnException() {
            when(selectorClient.callWithMetrics(any()))
                    .thenThrow(new RuntimeException("API error"));

            Optional<String> result = geminiClient.findSelector("Login button", snapshot);

            assertTrue(result.isEmpty());
            verify(metricsService, never()).recordElementFind(
                    any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean());
            verify(metricsService, never()).recordElementFindFailure(
                    any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyBoolean(), any());
        }
    }

    @Nested
    @DisplayName("planRepair")
    class PlanRepair {

        @Test
        @DisplayName("records repair plan metric on success")
        void recordsRepairPlanMetricOnSuccess() {
            AiResponse response = new AiResponse("[]", 200, 100, 300, MODEL_NAME);
            when(repairClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var failedStep = com.ai2qa.domain.factory.ActionStepFactory.click("Login button");
            var snapshot = DomSnapshot.of("content", "https://example.com", "Test");

            geminiClient.planRepair(failedStep, "Element not found", snapshot, TestPersona.STANDARD, null);

            verify(metricsService).recordRepairPlan(
                    eq(TENANT_ID),
                    isNull(),
                    eq(MODEL_PROVIDER),
                    eq(MODEL_NAME),
                    eq(200),
                    eq(100),
                    eq(300),
                    eq(true),
                    isNull()
            );
        }

        @Test
        @DisplayName("includes memory context in repair system prompt")
        void includesMemoryContextInRepairSystemPrompt() {
            AiResponse response = new AiResponse("[]", 200, 100, 300, MODEL_NAME);
            when(repairClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var failedStep = com.ai2qa.domain.factory.ActionStepFactory.click("Login button");
            var snapshot = DomSnapshot.of("content", "https://example.com", "Test");
            String memoryContext = "\n\n[GLOBAL HIVE MIND ACTIVATED]\n- [error:timeout]: Wait 3s before retry\n";

            geminiClient.planRepair(failedStep, "Timeout", snapshot, TestPersona.STANDARD, memoryContext);

            ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(repairClient).callWithMetrics(systemPromptCaptor.capture(), any(), any());

            String actualSystemPrompt = systemPromptCaptor.getValue();
            assertTrue(actualSystemPrompt.contains("[GLOBAL HIVE MIND ACTIVATED]"));
            assertTrue(actualSystemPrompt.contains("error:timeout"));
        }
    }

    @Nested
    @DisplayName("planGoalWithContext")
    class PlanGoalWithContext {

        @Test
        @DisplayName("records plan generation metric on success")
        void recordsPlanGenerationMetricOnSuccess() {
            AiResponse response = new AiResponse("[]", 300, 150, 400, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var snapshot = DomSnapshot.of("page content", "https://example.com", "Test Page");

            geminiClient.planGoalWithContext("Complete checkout", snapshot, TestPersona.STANDARD, null);

            verify(metricsService).recordPlanGeneration(
                    eq(TENANT_ID),
                    isNull(),
                    eq(MODEL_PROVIDER),
                    eq(MODEL_NAME),
                    eq(300),
                    eq(150),
                    eq(400),
                    eq(true),
                    isNull()
            );
        }

        @Test
        @DisplayName("includes memory context in plan with context system prompt")
        void includesMemoryContextInPlanWithContextSystemPrompt() {
            AiResponse response = new AiResponse("[]", 300, 150, 400, MODEL_NAME);
            when(plannerClient.callWithMetrics(any(), any(), any())).thenReturn(response);

            var snapshot = DomSnapshot.of("page content", "https://example.com", "Test Page");
            String memoryContext = "\n\n[GLOBAL HIVE MIND ACTIVATED]\n- [page:checkout]: Fill shipping before payment\n";

            geminiClient.planGoalWithContext("Complete checkout", snapshot, TestPersona.STANDARD, memoryContext);

            ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
            verify(plannerClient).callWithMetrics(systemPromptCaptor.capture(), any(), any());

            String actualSystemPrompt = systemPromptCaptor.getValue();
            assertTrue(actualSystemPrompt.contains("[GLOBAL HIVE MIND ACTIVATED]"));
            assertTrue(actualSystemPrompt.contains("page:checkout"));
            assertTrue(actualSystemPrompt.contains("shipping before payment"));
        }
    }
}
