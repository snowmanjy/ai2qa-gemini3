package com.ai2qa.application.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PromptTemplates network error injection.
 */
@DisplayName("PromptTemplates Network Errors")
class PromptTemplatesNetworkErrorsTest {

    @Nested
    @DisplayName("repairPlanningPrompt with network errors")
    class RepairPlanningWithNetworkErrorsTests {

        @Test
        @DisplayName("should include network errors section when errors present")
        void includesNetworkErrorsSection() {
            // Given
            List<String> networkErrors = List.of(
                    "[500 Internal Server Error] POST https://api.example.com/login",
                    "[401 Unauthorized] GET https://api.example.com/user"
            );

            // When
            String prompt = PromptTemplates.repairPlanningPrompt(
                    "click",
                    "login button",
                    "Element not clickable",
                    "<html>DOM snapshot</html>",
                    networkErrors
            );

            // Then
            assertThat(prompt).contains("[NETWORK ERRORS DETECTED]");
            assertThat(prompt).contains("500 Internal Server Error");
            assertThat(prompt).contains("401 Unauthorized");
            assertThat(prompt).contains("BLAME THE BACKEND");
        }

        @Test
        @DisplayName("should not include network section when no errors")
        void noNetworkSectionWhenNoErrors() {
            // When
            String prompt = PromptTemplates.repairPlanningPrompt(
                    "click",
                    "button",
                    "Element not found",
                    "<html>snapshot</html>",
                    List.of()
            );

            // Then
            assertThat(prompt).doesNotContain("[NETWORK ERRORS DETECTED]");
            assertThat(prompt).doesNotContain("BLAME THE BACKEND");
        }

        @Test
        @DisplayName("should handle null network errors")
        void handlesNullNetworkErrors() {
            // When
            String prompt = PromptTemplates.repairPlanningPrompt(
                    "type",
                    "email input",
                    "Field not visible",
                    "<html>snapshot</html>",
                    null
            );

            // Then
            assertThat(prompt).doesNotContain("[NETWORK ERRORS DETECTED]");
            assertThat(prompt).contains("Failed Action: type");
            assertThat(prompt).contains("Target: email input");
        }

        @Test
        @DisplayName("should preserve original prompt content")
        void preservesOriginalContent() {
            // Given
            List<String> errors = List.of("[500] POST /api");

            // When
            String prompt = PromptTemplates.repairPlanningPrompt(
                    "click",
                    "submit button",
                    "Timeout waiting for element",
                    "<button id='submit'>Submit</button>",
                    errors
            );

            // Then
            assertThat(prompt).contains("Failed Action: click");
            assertThat(prompt).contains("Target: submit button");
            assertThat(prompt).contains("Error: Timeout waiting for element");
            assertThat(prompt).contains("<button id='submit'>Submit</button>");
        }

        @Test
        @DisplayName("backward compatible - null network errors same as no-arg version")
        void backwardCompatible() {
            // When
            String promptNoNetwork = PromptTemplates.repairPlanningPrompt(
                    "click", "button", "error", "dom"
            );
            String promptWithNull = PromptTemplates.repairPlanningPrompt(
                    "click", "button", "error", "dom", null
            );

            // Then - Both should produce equivalent output
            assertThat(promptNoNetwork).contains("Failed Action: click");
            assertThat(promptWithNull).contains("Failed Action: click");
            assertThat(promptNoNetwork).doesNotContain("[NETWORK ERRORS DETECTED]");
            assertThat(promptWithNull).doesNotContain("[NETWORK ERRORS DETECTED]");
        }
    }

    @Nested
    @DisplayName("HEALER_SYSTEM_PROMPT")
    class HealerPromptTests {

        @Test
        @DisplayName("should include network error analysis guidance")
        void includesNetworkGuidance() {
            String prompt = PromptTemplates.HEALER_SYSTEM_PROMPT;

            assertThat(prompt).contains("NETWORK ERROR ANALYSIS");
            assertThat(prompt).contains("500 Internal Server Error");
            assertThat(prompt).contains("BLAME THE BACKEND");
            assertThat(prompt).contains("401 Unauthorized");
            assertThat(prompt).contains("403 Forbidden");
        }

        @Test
        @DisplayName("should include rootCause field in output format")
        void includesRootCauseField() {
            String prompt = PromptTemplates.HEALER_SYSTEM_PROMPT;

            assertThat(prompt).contains("rootCause");
            assertThat(prompt).contains("FRONTEND");
            assertThat(prompt).contains("BACKEND");
            assertThat(prompt).contains("NETWORK");
        }

        @Test
        @DisplayName("should include network failure in common patterns")
        void includesNetworkInCommonPatterns() {
            String prompt = PromptTemplates.HEALER_SYSTEM_PROMPT;

            assertThat(prompt).contains("Network failure");
            assertThat(prompt).contains("Backend API error");
        }
    }
}
