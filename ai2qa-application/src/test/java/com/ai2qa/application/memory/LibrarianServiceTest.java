package com.ai2qa.application.memory;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LibrarianService.
 */
@ExtendWith(MockitoExtension.class)
class LibrarianServiceTest {

    @Mock
    private ChatClientPort chatClient;

    private LibrarianService librarianService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        ByteArrayResource systemPrompt = new ByteArrayResource("Test system prompt".getBytes());
        ByteArrayResource jsonSchema = new ByteArrayResource("Test schema".getBytes());
        librarianService = new LibrarianService(chatClient, objectMapper, systemPrompt, jsonSchema);
    }

    @Nested
    class ExtractInsights {

        @Test
        void shouldExtractInsightsFromSuccessfulRun() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // Insights must be >= 100 chars and not use blocked tags
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "framework:react", "insightText": "Use data-testid attributes for stable selectors in test automation. These attributes are explicitly designed for testing and remain stable across refactors and style changes."},
                        {"contextTag": "spa:hydration", "insightText": "Client-side hydration must complete after initial page load on SPAs. Single-page applications often show content before JavaScript has fully initialized interactive elements."}
                    ],
                    "learningConfidence": "high"
                }
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(2);
            assertThat(insights.get(0).contextTag()).isEqualTo("framework:react");
            assertThat(insights.get(0).insightText()).contains("data-testid");
        }

        @Test
        void shouldReturnEmptyListForPendingRun() {
            // Given
            TestRun testRun = createPendingTestRun();

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).isEmpty();
        }

        @Test
        void shouldReturnEmptyListWhenNoExecutedSteps() {
            // Given
            TestRun testRun = createRunWithoutSteps();

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).isEmpty();
        }

        @Test
        void shouldHandleEmptyAiResponse() {
            // Given
            TestRun testRun = createCompletedTestRun();
            when(chatClient.call(anyString(), anyString(), any())).thenReturn("{\"insights\": []}");

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).isEmpty();
        }

        @Test
        void shouldHandleMalformedAiResponse() {
            // Given
            TestRun testRun = createCompletedTestRun();
            when(chatClient.call(anyString(), anyString(), any())).thenReturn("not json at all");

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).isEmpty();
        }

        @Test
        void shouldHandleMarkdownWrappedJson() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // Use non-blocked tag and insight >= 100 chars
            String aiResponse = """
                ```json
                {
                    "insights": [
                        {"contextTag": "strategy:selector", "insightText": "XPath selectors are reliable for navigating complex DOM structures when CSS selectors fail. Use relative XPath with contains() for partial text matching on dynamic content."}
                    ],
                    "learningConfidence": "medium"
                }
                ```
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).contextTag()).isEqualTo("strategy:selector");
        }

        @Test
        void shouldFilterInvalidInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // Valid insight must be >= 100 chars
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "", "insightText": "Empty tag - this insight has enough characters but the tag is empty so it should be filtered out by the validation logic"},
                        {"contextTag": "nocolon", "insightText": "Missing colon in tag - this insight has enough characters but the tag format is invalid without the colon separator"},
                        {"contextTag": "valid:tag", "insightText": "This is a valid insight with proper formatting. It contains enough characters to pass the minimum length requirement and uses correct tag taxonomy."}
                    ],
                    "learningConfidence": "medium"
                }
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).contextTag()).isEqualTo("valid:tag");
        }

        @Test
        void shouldLimitToFiveInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // All insights must be >= 100 chars to pass validation
            String longInsight = "This is a detailed insight that provides valuable information for test automation. It contains enough characters to meet the minimum length requirement for storage.";
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "strategy:first", "insightText": "%s"},
                        {"contextTag": "strategy:second", "insightText": "%s"},
                        {"contextTag": "strategy:third", "insightText": "%s"},
                        {"contextTag": "strategy:fourth", "insightText": "%s"},
                        {"contextTag": "strategy:fifth", "insightText": "%s"},
                        {"contextTag": "strategy:sixth", "insightText": "%s"},
                        {"contextTag": "strategy:seventh", "insightText": "%s"}
                    ],
                    "learningConfidence": "high"
                }
                """.formatted(longInsight, longInsight, longInsight, longInsight, longInsight, longInsight, longInsight);
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(5);
        }

        @Test
        void shouldSanitizePiiFromInsights() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // Insight must be >= 100 chars
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "auth:login", "insightText": "The email test@example.com failed to login due to timeout. This issue occurs consistently when the authentication service is under heavy load. Consider implementing retry logic with exponential backoff."}
                    ],
                    "learningConfidence": "medium"
                }
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).insightText()).contains("[EMAIL]");
            assertThat(insights.get(0).insightText()).doesNotContain("test@example.com");
            assertThat(insights.get(0).insightText()).contains("failed to login");
        }

        @Test
        void shouldExtractInsightsFromFailedRun() {
            // Given
            TestRun testRun = createFailedTestRun();
            // Use non-blocked tag and insight >= 100 chars
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "perf:slow_response", "insightText": "This site requires longer wait times for elements to become interactive. The server response time is consistently above average, requiring timeout adjustments in the automation configuration."}
                    ],
                    "learningConfidence": "high"
                }
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).contextTag()).isEqualTo("perf:slow_response");
        }

        @Test
        void shouldFilterBlockedTagPrefixes() {
            // Given
            TestRun testRun = createCompletedTestRun();
            // Valid-length insights but with blocked tag prefixes
            String validInsight = "This insight has enough characters and appears valid but its tag prefix is blocked because it represents a useless category that provides no actionable value for future test runs.";
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "site:media", "insightText": "%s"},
                        {"contextTag": "ui:button", "insightText": "%s"},
                        {"contextTag": "action:click", "insightText": "%s"},
                        {"contextTag": "consent:cookie", "insightText": "%s"},
                        {"contextTag": "framework:react", "insightText": "This is a valid insight about React framework that should pass all filters. It provides actionable guidance for test automation on React-based applications."}
                    ],
                    "learningConfidence": "medium"
                }
                """.formatted(validInsight, validInsight, validInsight, validInsight);
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then - only the framework:react insight should pass
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).contextTag()).isEqualTo("framework:react");
        }

        @Test
        void shouldFilterUselessPhrases() {
            // Given
            TestRun testRun = createCompletedTestRun();
            String aiResponse = """
                {
                    "insights": [
                        {"contextTag": "strategy:wait", "insightText": "Always remember to wait for the page to load completely before interacting. This is a best practice is to ensure all content is available before any interaction with the page elements."},
                        {"contextTag": "strategy:selector", "insightText": "Data-testid attributes provide stable element identification compared to CSS classes. These attributes survive refactoring and style changes, making automated tests more reliable over time."}
                    ],
                    "learningConfidence": "medium"
                }
                """;
            when(chatClient.call(anyString(), anyString(), any())).thenReturn(aiResponse);

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then - first insight contains "remember to" and "best practice is to" patterns, second is valid
            assertThat(insights).hasSize(1);
            assertThat(insights.get(0).contextTag()).isEqualTo("strategy:selector");
        }

        @Test
        void shouldHandleAiException() {
            // Given
            TestRun testRun = createCompletedTestRun();
            when(chatClient.call(anyString(), anyString(), any())).thenThrow(new RuntimeException("AI unavailable"));

            // When
            List<LibrarianService.ExtractedInsight> insights = librarianService.extractInsights(testRun);

            // Then
            assertThat(insights).isEmpty();
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

    private TestRun createFailedTestRun() {
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
        // Use retryCount=2 to indicate self-healing (required for failed runs to be learnable)
        ExecutedStep step2 = ExecutedStep.failed(plan.get(1), "Timeout", snapshot, 2, now);
        run.recordStepExecution(step1, now);
        run.recordStepExecution(step2, now);
        run.fail("Element not found", now);

        return run;
    }

    private TestRun createPendingTestRun() {
        TestRunId id = new TestRunId(UUID.randomUUID());
        return TestRun.create(id, "tenant-1", "https://example.com",
                List.of("Test login flow"), TestPersona.STANDARD, Instant.now());
    }

    private TestRun createRunWithoutSteps() {
        TestRunId id = new TestRunId(UUID.randomUUID());
        Instant now = Instant.now();
        TestRun run = TestRun.create(id, "tenant-1", "https://example.com",
                List.of("Test login flow"), TestPersona.STANDARD, now);
        run.start(List.of(), now);
        run.complete(now);
        return run;
    }
}
