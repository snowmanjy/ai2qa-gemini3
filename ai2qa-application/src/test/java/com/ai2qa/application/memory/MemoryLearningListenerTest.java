package com.ai2qa.application.memory;

import com.ai2qa.application.event.TestRunCompletedSpringEvent;
import com.ai2qa.application.knowledge.KnowledgeService;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorType;
import com.ai2qa.domain.model.knowledge.SitePattern;
import com.ai2qa.domain.model.knowledge.SitePatternId;
import com.ai2qa.domain.model.knowledge.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MemoryLearningListener.
 */
@ExtendWith(MockitoExtension.class)
class MemoryLearningListenerTest {

    @Mock
    private LibrarianService librarianService;

    @Mock
    private AgentMemoryService memoryService;

    @Mock
    private KnowledgeService knowledgeService;

    private MemoryLearningListener listener;

    @BeforeEach
    void setUp() {
        listener = new MemoryLearningListener(librarianService, memoryService, knowledgeService, true);
    }

    @Nested
    class OnTestRunCompleted {

        @Test
        void shouldExtractAndStoreInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);

            List<LibrarianService.ExtractedInsight> insights = List.of(
                    new LibrarianService.ExtractedInsight("framework:react", "Use data-testid"),
                    new LibrarianService.ExtractedInsight("wait:dynamic", "Wait for hydration")
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    insights, List.of(), Optional.empty(), "medium");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // When
            listener.onTestRunCompleted(event);

            // Then
            verify(librarianService).extractLearnings(testRun);
            verify(memoryService).appendInsight("framework:react", "Use data-testid");
            verify(memoryService).appendInsight("wait:dynamic", "Wait for hydration");
        }

        @Test
        void shouldNotStoreWhenNoInsightsExtracted() {
            // Given
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    List.of(), List.of(), Optional.empty(), "low");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // When
            listener.onTestRunCompleted(event);

            // Then
            verify(librarianService).extractLearnings(testRun);
            verify(memoryService, never()).appendInsight(any(), any());
        }

        @Test
        void shouldContinueStoringWhenOneInsightFails() {
            // Given
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);

            List<LibrarianService.ExtractedInsight> insights = List.of(
                    new LibrarianService.ExtractedInsight("tag:1", "Insight 1"),
                    new LibrarianService.ExtractedInsight("tag:2", "Insight 2"),
                    new LibrarianService.ExtractedInsight("tag:3", "Insight 3")
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    insights, List.of(), Optional.empty(), "medium");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // Make second insert fail
            org.mockito.Mockito.doNothing().when(memoryService).appendInsight(eq("tag:1"), any());
            org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                    .when(memoryService).appendInsight(eq("tag:2"), any());
            org.mockito.Mockito.doNothing().when(memoryService).appendInsight(eq("tag:3"), any());

            // When
            listener.onTestRunCompleted(event);

            // Then - all three should be attempted despite one failure
            verify(memoryService, times(3)).appendInsight(any(), any());
        }

        @Test
        void shouldNotProcessWhenLearningDisabled() {
            // Given
            listener = new MemoryLearningListener(librarianService, memoryService, knowledgeService, false);
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);

            // When
            listener.onTestRunCompleted(event);

            // Then
            verify(librarianService, never()).extractLearnings(any());
            verify(memoryService, never()).appendInsight(any(), any());
        }

        @Test
        void shouldHandleExtractionException() {
            // Given
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);
            when(librarianService.extractLearnings(testRun)).thenThrow(new RuntimeException("AI error"));

            // When - should not throw
            listener.onTestRunCompleted(event);

            // Then
            verify(memoryService, never()).appendInsight(any(), any());
        }

        @Test
        void shouldStoreStructuredPatterns() {
            // Given
            TestRun testRun = createCompletedTestRun();
            TestRunCompletedSpringEvent event = new TestRunCompletedSpringEvent(this, testRun);

            List<LibrarianService.ExtractedPattern> patterns = List.of(
                    new LibrarianService.ExtractedPattern(
                            "example.com",
                            PatternType.SELECTOR,
                            "login_button",
                            "[data-testid='login']",
                            SelectorType.CSS,
                            Optional.of("Login button selector"),
                            List.of(new LibrarianService.AlternativeSelector("#login-btn", SelectorType.CSS))
                    )
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    List.of(), patterns, Optional.of("react"), "high");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            SitePattern storedPattern = createTestSitePattern();
            when(knowledgeService.storePattern(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(storedPattern));

            // When
            listener.onTestRunCompleted(event);

            // Then
            verify(knowledgeService).storePattern(
                    eq("example.com"),
                    eq(PatternType.SELECTOR),
                    eq("login_button"),
                    eq("[data-testid='login']"),
                    any(),
                    any()
            );
            verify(knowledgeService).addSelectorAlternative(
                    eq(storedPattern.id()),
                    eq(SelectorType.CSS),
                    eq("#login-btn"),
                    eq(0)
            );
        }
    }

    @Nested
    class LearnFromRun {

        @Test
        void shouldReturnNumberOfStoredInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            List<LibrarianService.ExtractedInsight> insights = List.of(
                    new LibrarianService.ExtractedInsight("tag:1", "Insight 1"),
                    new LibrarianService.ExtractedInsight("tag:2", "Insight 2")
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    insights, List.of(), Optional.empty(), "medium");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // When
            int stored = listener.learnFromRun(testRun);

            // Then
            assertThat(stored).isEqualTo(2);
        }

        @Test
        void shouldReturnZeroWhenNoInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    List.of(), List.of(), Optional.empty(), "low");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // When
            int stored = listener.learnFromRun(testRun);

            // Then
            assertThat(stored).isZero();
        }

        @Test
        void shouldCountOnlySuccessfulStores() {
            // Given
            TestRun testRun = createCompletedTestRun();
            List<LibrarianService.ExtractedInsight> insights = List.of(
                    new LibrarianService.ExtractedInsight("tag:1", "Insight 1"),
                    new LibrarianService.ExtractedInsight("tag:2", "Insight 2"),
                    new LibrarianService.ExtractedInsight("tag:3", "Insight 3")
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    insights, List.of(), Optional.empty(), "medium");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            // Make middle insert fail
            org.mockito.Mockito.doNothing().when(memoryService).appendInsight(eq("tag:1"), any());
            org.mockito.Mockito.doThrow(new RuntimeException("DB error"))
                    .when(memoryService).appendInsight(eq("tag:2"), any());
            org.mockito.Mockito.doNothing().when(memoryService).appendInsight(eq("tag:3"), any());

            // When
            int stored = listener.learnFromRun(testRun);

            // Then
            assertThat(stored).isEqualTo(2);
        }

        @Test
        void shouldCountBothInsightsAndPatterns() {
            // Given
            TestRun testRun = createCompletedTestRun();
            List<LibrarianService.ExtractedInsight> insights = List.of(
                    new LibrarianService.ExtractedInsight("tag:1", "Insight 1")
            );
            List<LibrarianService.ExtractedPattern> patterns = List.of(
                    new LibrarianService.ExtractedPattern(
                            "example.com",
                            PatternType.SELECTOR,
                            "login_button",
                            "[data-testid='login']",
                            SelectorType.CSS,
                            Optional.empty(),
                            List.of()
                    )
            );
            LibrarianService.ExtractionResult result = new LibrarianService.ExtractionResult(
                    insights, patterns, Optional.empty(), "medium");
            when(librarianService.extractLearnings(testRun)).thenReturn(result);

            when(knowledgeService.storePattern(any(), any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(createTestSitePattern()));

            // When
            int stored = listener.learnFromRun(testRun);

            // Then
            assertThat(stored).isEqualTo(2); // 1 insight + 1 pattern
        }
    }

    // ==================== Helper Methods ====================

    private TestRun createCompletedTestRun() {
        TestRunId id = new TestRunId(UUID.randomUUID());
        Instant now = Instant.now();
        TestRun run = TestRun.create(id, "tenant-1", "https://example.com",
                List.of("Test login flow"), TestPersona.STANDARD, now);

        List<ActionStep> plan = List.of(
                ActionStepFactory.navigate("https://example.com"),
                ActionStepFactory.click("login button")
        );
        run.start(plan, now);

        DomSnapshot snapshot = DomSnapshot.of("content", "https://example.com", "Example");
        ExecutedStep step1 = ExecutedStep.success(plan.get(0), null, snapshot, snapshot, 100, now);
        ExecutedStep step2 = ExecutedStep.success(plan.get(1), "button#login", snapshot, snapshot, 200, now);
        run.recordStepExecution(step1, now);
        run.recordStepExecution(step2, now);

        return run;
    }

    private SitePattern createTestSitePattern() {
        Instant now = Instant.now();
        return new SitePattern(
                new SitePatternId(UUID.randomUUID()),
                "example.com",
                PatternType.SELECTOR,
                "login_button",
                "[data-testid='login']",
                BigDecimal.valueOf(0.5),
                0,
                0,
                Optional.empty(),
                Optional.empty(),
                now,
                Optional.empty(),
                Visibility.GLOBAL,
                Optional.empty(),
                0
        );
    }
}
