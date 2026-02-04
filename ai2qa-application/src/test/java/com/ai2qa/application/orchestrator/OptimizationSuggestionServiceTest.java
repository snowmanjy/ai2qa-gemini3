package com.ai2qa.application.orchestrator;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.application.security.PromptSanitizer;
import com.ai2qa.domain.model.DomSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OptimizationSuggestionService.
 *
 * Tests the AI-powered suggestion generation for test step analysis.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationSuggestionServiceTest {

    @Mock
    private ChatClientPort chatClient;

    @Mock
    private PromptSanitizer promptSanitizer;

    private OptimizationSuggestionService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();

        // Create service with mock system prompt
        ByteArrayResource mockPrompt = new ByteArrayResource("Test system prompt".getBytes());

        // Default: sanitizer returns input unchanged
        lenient().when(promptSanitizer.sanitizeText(anyString())).thenAnswer(inv -> inv.getArgument(0));

        service = new OptimizationSuggestionService(
                chatClient,
                objectMapper,
                promptSanitizer,
                mockPrompt
        );
    }

    @Nested
    @DisplayName("Suggestion Generation")
    class SuggestionGeneration {

        @Test
        @DisplayName("Should return empty when no errors and step was successful")
        void generateSuggestion_NoErrorsAndSuccessful_ReturnsEmpty() {
            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "login button",
                    "button#login",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isEmpty();
            verifyNoInteractions(chatClient);
        }

        @Test
        @DisplayName("Should generate suggestion when console errors present")
        void generateSuggestion_WithConsoleErrors_GeneratesSuggestion() {
            // Arrange
            String aiResponse = """
                    {
                        "hasSuggestion": true,
                        "suggestion": "Consider adding null check for user object before accessing properties.",
                        "rootCause": "FRONTEND",
                        "severity": "HIGH"
                    }
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            List<String> consoleErrors = List.of(
                    "TypeError: Cannot read property 'name' of null",
                    "Uncaught ReferenceError: user is not defined"
            );

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "submit button",
                    "button[type='submit']",
                    consoleErrors,
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).contains("null check");
            verify(chatClient).call(anyString(), anyString(), eq(0.3));
        }

        @Test
        @DisplayName("Should generate suggestion when network errors present")
        void generateSuggestion_WithNetworkErrors_GeneratesSuggestion() {
            // Arrange
            String aiResponse = """
                    {
                        "hasSuggestion": true,
                        "suggestion": "API endpoint /api/user returned 500. Check backend server logs.",
                        "rootCause": "BACKEND",
                        "severity": "HIGH"
                    }
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            List<String> networkErrors = List.of(
                    "500 Internal Server Error: GET /api/user"
            );

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "load user button",
                    null,
                    Collections.emptyList(),
                    networkErrors,
                    null,
                    true
            );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).contains("API endpoint");
        }

        @Test
        @DisplayName("Should generate suggestion when step failed even without errors")
        void generateSuggestion_FailedStepWithoutErrors_GeneratesSuggestion() {
            // Arrange
            String aiResponse = """
                    {
                        "hasSuggestion": true,
                        "suggestion": "Element selector may be too brittle. Consider using data-testid.",
                        "rootCause": "SELECTOR",
                        "severity": "MEDIUM"
                    }
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            DomSnapshot snapshot = DomSnapshot.of("<div><button class=\"btn-login\">Login</button></div>", "https://example.com", "Test Page");

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "login button",
                    "button.btn-login",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    snapshot,
                    false  // wasSuccessful = false
            );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).contains("data-testid");
        }

        @Test
        @DisplayName("Should return empty when AI says no suggestion needed")
        void generateSuggestion_AiSaysNoSuggestion_ReturnsEmpty() {
            // Arrange
            String aiResponse = """
                    {
                        "hasSuggestion": false,
                        "suggestion": null,
                        "rootCause": null,
                        "severity": null
                    }
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            List<String> consoleErrors = List.of("Warning: Minor deprecation notice");

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    consoleErrors,
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Response Parsing")
    class ResponseParsing {

        @Test
        @DisplayName("Should handle markdown code block in response")
        void generateSuggestion_MarkdownCodeBlock_ParsesCorrectly() {
            // Arrange
            String aiResponse = """
                    ```json
                    {
                        "hasSuggestion": true,
                        "suggestion": "Add error boundary component.",
                        "rootCause": "FRONTEND",
                        "severity": "MEDIUM"
                    }
                    ```
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    List.of("Error in render"),
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo("Add error boundary component.");
        }

        @Test
        @DisplayName("Should return empty on malformed JSON response")
        void generateSuggestion_MalformedJson_ReturnsEmpty() {
            // Arrange
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn("not valid json at all");

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    List.of("Error"),
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when chat client throws exception")
        void generateSuggestion_ChatClientThrows_ReturnsEmpty() {
            // Arrange
            when(chatClient.call(anyString(), anyString(), anyDouble()))
                    .thenThrow(new RuntimeException("API error"));

            // Act
            Optional<String> result = service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    List.of("Error"),
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Prompt Building")
    class PromptBuilding {

        @Test
        @DisplayName("Should sanitize console errors in prompt")
        void generateSuggestion_SanitizesConsoleErrors() {
            // Arrange
            String aiResponse = """
                    {"hasSuggestion": true, "suggestion": "Fix it", "rootCause": "FRONTEND", "severity": "LOW"}
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);
            when(promptSanitizer.sanitizeText("malicious<script>")).thenReturn("malicious");

            List<String> consoleErrors = List.of("malicious<script>");

            // Act
            service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    consoleErrors,
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert
            verify(promptSanitizer).sanitizeText("malicious<script>");
        }

        @Test
        @DisplayName("Should truncate large DOM snapshots")
        void generateSuggestion_LargeDom_Truncates() {
            // Arrange
            String aiResponse = """
                    {"hasSuggestion": true, "suggestion": "Fix it", "rootCause": "FRONTEND", "severity": "LOW"}
                    """;
            when(chatClient.call(anyString(), contains("truncated"), anyDouble())).thenReturn(aiResponse);

            // Create DOM larger than MAX_DOM_LENGTH (3000)
            String largeDom = "<div>" + "x".repeat(4000) + "</div>";
            DomSnapshot snapshot = DomSnapshot.of(largeDom, "https://example.com", "Test Page");

            // Act
            service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    List.of("Error"),
                    Collections.emptyList(),
                    snapshot,
                    false
            );

            // Assert - verify truncated prompt was used
            verify(chatClient).call(anyString(), contains("truncated"), anyDouble());
        }

        @Test
        @DisplayName("Should limit number of errors analyzed")
        void generateSuggestion_ManyErrors_LimitsToMax() {
            // Arrange
            String aiResponse = """
                    {"hasSuggestion": true, "suggestion": "Fix it", "rootCause": "FRONTEND", "severity": "LOW"}
                    """;
            when(chatClient.call(anyString(), anyString(), anyDouble())).thenReturn(aiResponse);

            // Create more than MAX_ERRORS_TO_ANALYZE (5)
            List<String> manyErrors = List.of(
                    "Error 1", "Error 2", "Error 3", "Error 4", "Error 5",
                    "Error 6", "Error 7", "Error 8"
            );

            // Act
            service.generateSuggestion(
                    "click",
                    "button",
                    null,
                    manyErrors,
                    Collections.emptyList(),
                    null,
                    true
            );

            // Assert - verify the prompt mentions additional errors were omitted
            verify(chatClient).call(anyString(), contains("and 3 more errors"), anyDouble());
        }
    }
}
