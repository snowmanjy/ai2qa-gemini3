package com.ai2qa.application.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PromptTemplates mode-aware functionality.
 * Tests the aria vs legacy mode prompt selection.
 */
@DisplayName("PromptTemplates Mode-Aware")
class PromptTemplatesModeAwareTest {

    @Nested
    @DisplayName("getHunterSystemPrompt")
    class GetHunterSystemPromptTests {

        @Test
        @DisplayName("should return legacy prompt when useAriaMode is false")
        void returnsLegacyPromptWhenNotAriaMode() {
            String prompt = PromptTemplates.getHunterSystemPrompt(false);

            assertThat(prompt).isEqualTo(PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY);
            assertThat(prompt).contains("CSS selector");
            assertThat(prompt).contains("[s0]");
            assertThat(prompt).contains("selector=\"button#login\"");
            assertThat(prompt).doesNotContain("[ref=e1]");
        }

        @Test
        @DisplayName("should return aria prompt when useAriaMode is true")
        void returnsAriaPromptWhenAriaMode() {
            String prompt = PromptTemplates.getHunterSystemPrompt(true);

            assertThat(prompt).isEqualTo(PromptTemplates.HUNTER_SYSTEM_PROMPT_ARIA);
            assertThat(prompt).contains("Accessibility Tree");
            assertThat(prompt).contains("[ref=e1]");
            assertThat(prompt).contains("@e1");
            assertThat(prompt).doesNotContain("[s0]");
            assertThat(prompt).doesNotContain("selector=\"");
        }
    }

    @Nested
    @DisplayName("HUNTER_SYSTEM_PROMPT_LEGACY")
    class HunterLegacyPromptTests {

        @Test
        @DisplayName("should contain CSS selector format example")
        void containsCssSelectorFormat() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY;

            assertThat(prompt).contains("[s0] button \"Login\" selector=\"button#login\"");
            assertThat(prompt).contains("[s1] input \"Email\"");
        }

        @Test
        @DisplayName("should instruct to return CSS selector")
        void instructsReturnCssSelector() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY;

            assertThat(prompt).contains("Return ONLY the CSS selector");
            assertThat(prompt).contains("button#login");
        }

        @Test
        @DisplayName("should mention data-testid as preferred identifier")
        void mentionsDataTestId() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY;

            assertThat(prompt).contains("data-testid");
            assertThat(prompt).contains("aria-label");
        }
    }

    @Nested
    @DisplayName("HUNTER_SYSTEM_PROMPT_ARIA")
    class HunterAriaPromptTests {

        @Test
        @DisplayName("should contain ref format example")
        void containsRefFormat() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_ARIA;

            assertThat(prompt).contains("- button \"Login\" [ref=e1]");
            assertThat(prompt).contains("- textbox \"Email\" [ref=e2]");
            assertThat(prompt).contains("- link \"Forgot password?\" [ref=e3]");
        }

        @Test
        @DisplayName("should instruct to return ref")
        void instructsReturnRef() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_ARIA;

            assertThat(prompt).contains("Return the ref");
            assertThat(prompt).contains("@e1");
            assertThat(prompt).contains("Return ONLY the ref");
        }

        @Test
        @DisplayName("should mention accessible role and name")
        void mentionsAccessibility() {
            String prompt = PromptTemplates.HUNTER_SYSTEM_PROMPT_ARIA;

            assertThat(prompt).contains("accessible role");
            assertThat(prompt).contains("accessibility tree");
        }
    }

    @Nested
    @DisplayName("selectorFinderPrompt with mode")
    class SelectorFinderPromptWithModeTests {

        @Test
        @DisplayName("should generate legacy prompt when useAriaMode is false")
        void generatesLegacyPrompt() {
            String prompt = PromptTemplates.selectorFinderPrompt(
                    "login button",
                    "[s0] button \"Login\" selector=\"button#login\"",
                    false
            );

            assertThat(prompt).contains("Find the element matching this description: \"login button\"");
            assertThat(prompt).contains("DOM Snapshot:");
            assertThat(prompt).contains("Return the CSS selector");
            assertThat(prompt).contains("button#login");
            assertThat(prompt).doesNotContain("Accessibility Tree");
            assertThat(prompt).doesNotContain("@e1");
        }

        @Test
        @DisplayName("should generate aria prompt when useAriaMode is true")
        void generatesAriaPrompt() {
            String prompt = PromptTemplates.selectorFinderPrompt(
                    "login button",
                    "- button \"Login\" [ref=e1]",
                    true
            );

            assertThat(prompt).contains("Find the element matching this description: \"login button\"");
            assertThat(prompt).contains("Accessibility Tree:");
            assertThat(prompt).contains("Return the ref");
            assertThat(prompt).contains("@e1");
            assertThat(prompt).doesNotContain("DOM Snapshot");
            assertThat(prompt).doesNotContain("CSS selector");
        }

        @Test
        @DisplayName("should include snapshot content in prompt")
        void includesSnapshotContent() {
            String snapshot = "- textbox \"Email\" [ref=e1]\n- button \"Submit\" [ref=e2]";

            String prompt = PromptTemplates.selectorFinderPrompt(
                    "email field",
                    snapshot,
                    true
            );

            assertThat(prompt).contains("textbox \"Email\"");
            assertThat(prompt).contains("button \"Submit\"");
        }

        @Test
        @DisplayName("should include NOT_FOUND instruction")
        void includesNotFoundInstruction() {
            String legacyPrompt = PromptTemplates.selectorFinderPrompt("btn", "dom", false);
            String ariaPrompt = PromptTemplates.selectorFinderPrompt("btn", "tree", true);

            assertThat(legacyPrompt).contains("NOT_FOUND");
            assertThat(ariaPrompt).contains("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("selectorFinderPrompt backward compatibility")
    class SelectorFinderBackwardCompatibilityTests {

        @Test
        @DisplayName("deprecated method should default to legacy mode")
        @SuppressWarnings("deprecation")
        void deprecatedMethodDefaultsToLegacy() {
            String deprecatedPrompt = PromptTemplates.selectorFinderPrompt(
                    "button",
                    "[s0] button selector=\"#btn\""
            );

            String explicitLegacyPrompt = PromptTemplates.selectorFinderPrompt(
                    "button",
                    "[s0] button selector=\"#btn\"",
                    false
            );

            // Both should be equivalent
            assertThat(deprecatedPrompt).contains("DOM Snapshot");
            assertThat(deprecatedPrompt).contains("CSS selector");
            assertThat(explicitLegacyPrompt).contains("DOM Snapshot");
        }
    }

    @Nested
    @DisplayName("Legacy constant compatibility")
    class LegacyConstantTests {

        @Test
        @DisplayName("HUNTER_SYSTEM_PROMPT should equal HUNTER_SYSTEM_PROMPT_LEGACY")
        @SuppressWarnings("deprecation")
        void legacyConstantEqualsLegacyPrompt() {
            assertThat(PromptTemplates.HUNTER_SYSTEM_PROMPT)
                    .isEqualTo(PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY);
        }
    }
}
