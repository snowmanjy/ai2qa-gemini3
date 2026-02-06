package com.ai2qa.application.orchestrator;

import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.DomSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Reflector component.
 */
class ReflectorTest {

    private Reflector reflector;

    @BeforeEach
    void setUp() {
        reflector = new Reflector(new TestOrchestratorConfig());
    }

    @Nested
    class NavigationReflection {

        @Test
        void shouldSucceedWhenNavigationHasUrl() {
            // Given
            ActionStep action = ActionStepFactory.navigate("https://example.com");
            DomSnapshot before = DomSnapshot.empty();
            DomSnapshot after = DomSnapshot.of("content", "https://example.com", "Example");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void shouldRetryWhenNavigationHasNoUrl() {
            // Given
            ActionStep action = ActionStepFactory.navigate("https://example.com");
            DomSnapshot before = DomSnapshot.empty();
            DomSnapshot after = DomSnapshot.of("content", "", "");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getRepairSteps()).hasSize(1);
            assertThat(result.getRepairSteps().get(0).action()).isEqualTo("wait");
        }
    }

    @Nested
    class ClickReflection {

        @Test
        void shouldSucceedWhenDomChangesAfterClick() {
            // Given
            ActionStep action = ActionStepFactory.clickSelector("login button", "button#login");
            DomSnapshot before = DomSnapshot.of("Login form", "url", "title");
            DomSnapshot after = DomSnapshot.of("Dashboard loaded", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Success.class);
            assertThat(((ReflectionResult.Success) result).selectorUsed()).isEqualTo("button#login");
        }

        @Test
        void shouldWaitWhenDomUnchangedAfterClick() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("same content", "url", "title");
            DomSnapshot after = DomSnapshot.of("same content", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isWait()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Wait.class);
            assertThat(((ReflectionResult.Wait) result).waitMs()).isGreaterThan(0);
        }

        @Test
        void shouldSucceedAfterMaxRetriesWhenDomUnchanged_preventInfiniteLoop() {
            // Given - This test prevents infinite loop regression
            // Some clicks (analytics, tracking) don't cause DOM changes
            ActionStep action = ActionStepFactory.clickSelector("analytics button", "button#track");
            DomSnapshot before = DomSnapshot.of("same content", "url", "title");
            DomSnapshot after = DomSnapshot.of("same content", "url", "title");

            // When - at MAX_RETRIES (3), should stop waiting and assume success
            ReflectionResult result = reflector.reflect(action, before, after, null, 3);

            // Then - should succeed to prevent infinite loop
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isWait()).isFalse();
        }

        @Test
        void shouldContinueWaitingBeforeMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("same content", "url", "title");
            DomSnapshot after = DomSnapshot.of("same content", "url", "title");

            // When - still below MAX_RETRIES
            ReflectionResult result = reflector.reflect(action, before, after, null, 2);

            // Then - should still wait
            assertThat(result.isWait()).isTrue();
        }
    }

    @Nested
    class TypeReflection {

        @Test
        void shouldSucceedWhenTypedValueAppearsInDom() {
            // Given
            ActionStep action = ActionStepFactory.type("email field", "test@example.com");
            DomSnapshot before = DomSnapshot.of("input empty", "url", "title");
            DomSnapshot after = DomSnapshot.of("input: test@example.com", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void shouldSucceedWhenDomChangesEvenWithoutVisibleValue() {
            // Given - password field where value is masked
            ActionStep action = ActionStepFactory.type("password field", "secret123");
            DomSnapshot before = DomSnapshot.of("password empty", "url", "title");
            DomSnapshot after = DomSnapshot.of("password: ****", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class WaitAndScreenshotReflection {

        @Test
        void shouldAlwaysSucceedForWaitAction() {
            // Given
            ActionStep action = ActionStepFactory.waitFor("element", 1000);
            DomSnapshot before = DomSnapshot.empty();
            DomSnapshot after = DomSnapshot.empty();

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void shouldAlwaysSucceedForScreenshotAction() {
            // Given
            ActionStep action = ActionStepFactory.screenshot("current state");
            DomSnapshot before = DomSnapshot.empty();
            DomSnapshot after = DomSnapshot.empty();

            // When
            ReflectionResult result = reflector.reflect(action, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldRetryOnElementNotFoundError() {
            // Given
            ActionStep action = ActionStepFactory.click("missing button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found: button#missing";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 0);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getReason()).contains("Element not found");
        }

        @Test
        void shouldRetryOnTimeoutError() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Timeout waiting for element";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 0);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getRepairSteps()).isNotEmpty();
            assertThat(result.getRepairSteps().get(0).action()).isEqualTo("wait");
        }

        @Test
        void shouldSkipAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When - retry count at max (3)
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip and continue, not abort the entire run
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("skipped after 4 attempts");
        }

        @Test
        void shouldHandleMissingAfterSnapshot() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(action, before, null, null, 0);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getReason()).contains("No snapshot after execution");
        }

        @Test
        void shouldRetryGenericErrorsWithinRetryLimit() {
            // Given
            ActionStep action = ActionStepFactory.click("button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Some unexpected error";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 1);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getReason()).contains("Retrying action");
        }
    }

    @Nested
    class UnknownActions {

        @Test
        void shouldSucceedForUnknownActionTypeWithSelector() {
            // Given
            ActionStep action = ActionStepFactory.of("custom_action", "target");
            ActionStep withSelector = ActionStepFactory.withSelector(action, "custom-selector");
            DomSnapshot before = DomSnapshot.empty();
            DomSnapshot after = DomSnapshot.of("content", "url", "title");

            // When
            ReflectionResult result = reflector.reflect(withSelector, before, after, null, 0);

            // Then
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Nested
    class OptionalStepSkipping {

        @Test
        void shouldSkipCookieConsentAfterMaxRetries() {
            // Given - Cookie consent button not found
            ActionStep action = ActionStepFactory.click("Accept Cookies Button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found: Accept Cookies Button";

            // When - at max retry count (3)
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip, not abort
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("skipped");
        }

        @Test
        void shouldSkipCookieBannerAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("cookie banner accept");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipGdprConsentAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("GDPR consent button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipPrivacyAcceptAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Accept privacy settings");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipNewsletterPopupAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Newsletter popup dismiss");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipChatWidgetAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Close chat widget");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipNonOptionalStepAfterMaxRetries() {
            // Given - Even non-optional steps skip after max retries to avoid aborting the run
            ActionStep action = ActionStepFactory.click("Submit Form Button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip (step recorded as SKIPPED in report)
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("skipped after 4 attempts");
        }

        @Test
        void shouldSkipLegalAgreeButtonAfterMaxRetries() {
            // Given - Legal popup "Agree" button (like CNN's TOS popup)
            ActionStep action = ActionStepFactory.click("Agree button on legal popup");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip, not abort
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipTermsOfServiceAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Accept Terms of Service");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipAdFeedbackAfterMaxRetries() {
            // Given - Ad feedback popup (common on CNN)
            ActionStep action = ActionStepFactory.click("Dismiss ad feedback");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipCloseWelcomeBannerAfterMaxRetries() {
            // Given - "Close Welcome Banner" already handled by auto-dismiss
            ActionStep action = ActionStepFactory.click("Close Welcome Banner");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip with dismiss-specific message, not abort
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("Dismiss step");
            assertThat(result.getReason()).contains("already handled");
        }

        @Test
        void shouldSkipDismissNotificationAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Dismiss notification");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("Dismiss step");
        }

        @Test
        void shouldSkipCloseDialogAfterMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Close dialog");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then
            assertThat(result.isSkip()).isTrue();
        }

        @Test
        void shouldSkipCloseProductDetailsButtonAfterMaxRetries() {
            // Given - "close product details button" = a close button for a modal
            // The AI names the target after the button, not the modal itself
            ActionStep action = ActionStepFactory.click("close product details button");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - should skip (dismiss verb + "button" = close button pattern)
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("Dismiss step");
        }

        @Test
        void shouldSkipGenericCloseActionWithGenericMessage() {
            // Given - "Close the account settings" is not a dismiss action
            ActionStep action = ActionStepFactory.click("Close the account settings");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When
            ReflectionResult result = reflector.reflect(action, before, null, error, 3);

            // Then - skips like all steps, but with generic message (not dismiss-specific)
            assertThat(result.isSkip()).isTrue();
            assertThat(result.getReason()).contains("skipped after 4 attempts");
            assertThat(result.getReason()).doesNotContain("Dismiss step");
        }

        @Test
        void shouldRetryCookieConsentBeforeMaxRetries() {
            // Given
            ActionStep action = ActionStepFactory.click("Accept Cookies");
            DomSnapshot before = DomSnapshot.of("content", "url", "title");
            String error = "Element not found";

            // When - still have retries left
            ReflectionResult result = reflector.reflect(action, before, null, error, 0);

            // Then - should retry, not skip yet
            assertThat(result.isRetry()).isTrue();
        }
    }
}
