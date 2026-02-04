package com.ai2qa.application.planner;

import com.ai2qa.application.memory.AgentMemoryService;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.TestPersona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("StepPlanner")
class StepPlannerTest {

    private StepPlanner.GeminiPlannerClient geminiClient;
    private AgentMemoryService memoryService;
    private StepPlanner stepPlanner;

    @BeforeEach
    void setUp() {
        geminiClient = mock(StepPlanner.GeminiPlannerClient.class);
        memoryService = mock(AgentMemoryService.class);
        stepPlanner = new StepPlanner(geminiClient, memoryService);
    }

    @Nested
    @DisplayName("Memory Context Injection")
    class MemoryContextInjection {

        @Test
        @DisplayName("createPlan loads memory and passes to planGoal")
        void createPlanLoadsMemoryAndPassesToPlanGoal() {
            // Given
            Map<String, String> memoryMap = Map.of(
                    "framework:react", "Use data-testid for stable selectors",
                    "error:hydration", "Wait for hydration complete before clicking"
            );
            when(memoryService.loadMemoryMap()).thenReturn(memoryMap);
            when(geminiClient.planGoal(anyString(), anyString(), any(TestPersona.class), anyString()))
                    .thenReturn(List.of(ActionStepFactory.click("Login button")));

            // When
            stepPlanner.createPlan("https://example.com", List.of("Login"), TestPersona.STANDARD);

            // Then
            ArgumentCaptor<String> memoryCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).planGoal(eq("Login"), eq("https://example.com"), eq(TestPersona.STANDARD), memoryCaptor.capture());

            String memoryContext = memoryCaptor.getValue();
            assertTrue(memoryContext.contains("[GLOBAL HIVE MIND ACTIVATED]"));
            assertTrue(memoryContext.contains("framework:react"));
            assertTrue(memoryContext.contains("data-testid"));
        }

        @Test
        @DisplayName("createPlan passes empty string when memory is empty")
        void createPlanPassesEmptyStringWhenMemoryEmpty() {
            // Given
            when(memoryService.loadMemoryMap()).thenReturn(Map.of());
            when(geminiClient.planGoal(anyString(), anyString(), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            // When
            stepPlanner.createPlan("https://example.com", List.of("Login"), TestPersona.STANDARD);

            // Then
            ArgumentCaptor<String> memoryCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).planGoal(eq("Login"), eq("https://example.com"), eq(TestPersona.STANDARD), memoryCaptor.capture());

            String memoryContext = memoryCaptor.getValue();
            assertEquals("", memoryContext);
        }

        @Test
        @DisplayName("planForGoal loads memory and passes to planGoalWithContext")
        void planForGoalLoadsMemoryAndPassesToPlanGoalWithContext() {
            // Given
            Map<String, String> memoryMap = Map.of(
                    "page:checkout", "Fill shipping before payment"
            );
            when(memoryService.loadMemoryMap()).thenReturn(memoryMap);
            when(geminiClient.planGoalWithContext(anyString(), any(DomSnapshot.class), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            DomSnapshot snapshot = DomSnapshot.of("content", "https://example.com", "Test Page");

            // When
            stepPlanner.planForGoal("Complete checkout", snapshot, TestPersona.STANDARD);

            // Then
            ArgumentCaptor<String> memoryCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).planGoalWithContext(eq("Complete checkout"), eq(snapshot), eq(TestPersona.STANDARD), memoryCaptor.capture());

            String memoryContext = memoryCaptor.getValue();
            assertTrue(memoryContext.contains("page:checkout"));
            assertTrue(memoryContext.contains("shipping before payment"));
        }

        @Test
        @DisplayName("createRepairPlan loads memory and passes to planRepair")
        void createRepairPlanLoadsMemoryAndPassesToPlanRepair() {
            // Given
            Map<String, String> memoryMap = Map.of(
                    "error:timeout", "Wait 3s before retry on timeout errors"
            );
            when(memoryService.loadMemoryMap()).thenReturn(memoryMap);
            when(geminiClient.planRepair(any(ActionStep.class), anyString(), any(DomSnapshot.class), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            ActionStep failedStep = ActionStepFactory.click("Submit button");
            DomSnapshot snapshot = DomSnapshot.of("content", "https://example.com", "Test Page");

            // When
            stepPlanner.createRepairPlan(failedStep, "Timeout waiting for element", snapshot, TestPersona.STANDARD);

            // Then
            ArgumentCaptor<String> memoryCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).planRepair(eq(failedStep), eq("Timeout waiting for element"), eq(snapshot), eq(TestPersona.STANDARD), memoryCaptor.capture());

            String memoryContext = memoryCaptor.getValue();
            assertTrue(memoryContext.contains("error:timeout"));
            assertTrue(memoryContext.contains("Wait 3s before retry"));
        }

        @Test
        @DisplayName("handles memory service exception gracefully")
        void handlesMemoryServiceExceptionGracefully() {
            // Given
            when(memoryService.loadMemoryMap()).thenThrow(new RuntimeException("Database error"));
            when(geminiClient.planGoal(anyString(), anyString(), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            // When - Should not throw
            List<ActionStep> result = stepPlanner.createPlan("https://example.com", List.of("Login"), TestPersona.STANDARD);

            // Then - Empty memory context passed but operation continues
            ArgumentCaptor<String> memoryCaptor = ArgumentCaptor.forClass(String.class);
            verify(geminiClient).planGoal(anyString(), anyString(), any(TestPersona.class), memoryCaptor.capture());

            String memoryContext = memoryCaptor.getValue();
            assertEquals("", memoryContext);
        }
    }

    @Nested
    @DisplayName("Default Persona")
    class DefaultPersona {

        @Test
        @DisplayName("createPlan uses STANDARD persona when null provided")
        void createPlanUsesStandardPersonaWhenNullProvided() {
            // Given
            when(memoryService.loadMemoryMap()).thenReturn(Map.of());
            when(geminiClient.planGoal(anyString(), anyString(), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            // When
            stepPlanner.createPlan("https://example.com", List.of("Login"), (TestPersona) null);

            // Then
            verify(geminiClient).planGoal(eq("Login"), eq("https://example.com"), eq(TestPersona.STANDARD), any());
        }

        @Test
        @DisplayName("planForGoal uses STANDARD persona when null provided")
        void planForGoalUsesStandardPersonaWhenNullProvided() {
            // Given
            when(memoryService.loadMemoryMap()).thenReturn(Map.of());
            when(geminiClient.planGoalWithContext(anyString(), any(DomSnapshot.class), any(TestPersona.class), anyString()))
                    .thenReturn(List.of());

            DomSnapshot snapshot = DomSnapshot.of("content", "https://example.com", "Test Page");

            // When
            stepPlanner.planForGoal("Complete checkout", snapshot, (TestPersona) null);

            // Then
            verify(geminiClient).planGoalWithContext(anyString(), any(DomSnapshot.class), eq(TestPersona.STANDARD), any());
        }
    }

    @Nested
    @DisplayName("Navigation Step")
    class NavigationStep {

        @Test
        @DisplayName("createPlan always starts with navigation step")
        void createPlanAlwaysStartsWithNavigationStep() {
            // Given
            when(memoryService.loadMemoryMap()).thenReturn(Map.of());
            when(geminiClient.planGoal(anyString(), anyString(), any(TestPersona.class), anyString()))
                    .thenReturn(List.of(ActionStepFactory.click("Login")));

            // When
            List<ActionStep> plan = stepPlanner.createPlan("https://example.com", List.of("Login"), TestPersona.STANDARD);

            // Then
            assertFalse(plan.isEmpty());
            assertEquals("navigate", plan.get(0).action());
            assertEquals("https://example.com", plan.get(0).target());
        }
    }
}
