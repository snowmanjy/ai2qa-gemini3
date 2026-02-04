package com.ai2qa.application.orchestrator;

import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ReflectionResult sealed interface.
 */
class ReflectionResultTest {

    @Nested
    class SuccessResult {

        @Test
        void shouldCreateSuccessWithSelector() {
            // When
            ReflectionResult result = ReflectionResult.success("button#submit");

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.isRetry()).isFalse();
            assertThat(result.isWait()).isFalse();
            assertThat(result.isAbort()).isFalse();
        }

        @Test
        void shouldCreateSuccessWithNullSelector() {
            // When
            ReflectionResult result = ReflectionResult.success(null);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Success.class);
            assertThat(((ReflectionResult.Success) result).selectorUsed()).isNull();
        }

        @Test
        void shouldReturnEmptyRepairStepsForSuccess() {
            // When
            ReflectionResult result = ReflectionResult.success("selector");

            // Then
            assertThat(result.getRepairSteps()).isEmpty();
        }

        @Test
        void shouldReturnReasonForSuccess() {
            // When
            ReflectionResult result = ReflectionResult.success("button#submit");

            // Then
            assertThat(result.getReason()).contains("Success with selector: button#submit");
        }
    }

    @Nested
    class RetryResult {

        @Test
        void shouldCreateRetryWithSingleRepairStep() {
            // Given
            ActionStep repairStep = ActionStepFactory.waitFor("element", 1000);

            // When
            ReflectionResult result = ReflectionResult.retry("Element not found", repairStep);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getRepairSteps()).hasSize(1);
            assertThat(result.getRepairSteps().get(0)).isEqualTo(repairStep);
        }

        @Test
        void shouldCreateRetryWithMultipleRepairSteps() {
            // Given
            List<ActionStep> repairSteps = List.of(
                    ActionStepFactory.waitFor("element", 1000),
                    ActionStepFactory.click("retry button")
            );

            // When
            ReflectionResult result = ReflectionResult.retry("Failed, retrying", repairSteps);

            // Then
            assertThat(result.isRetry()).isTrue();
            assertThat(result.getRepairSteps()).hasSize(2);
        }

        @Test
        void shouldReturnReasonForRetry() {
            // When
            ReflectionResult result = ReflectionResult.retry("Element not found", ActionStepFactory.click("btn"));

            // Then
            assertThat(result.getReason()).isEqualTo("Element not found");
        }

        @Test
        void shouldNotBeSuccessWaitOrAbort() {
            // When
            ReflectionResult result = ReflectionResult.retry("reason", ActionStepFactory.click("btn"));

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isWait()).isFalse();
            assertThat(result.isAbort()).isFalse();
        }
    }

    @Nested
    class WaitResult {

        @Test
        void shouldCreateWaitWithDuration() {
            // When
            ReflectionResult result = ReflectionResult.waitFor("Waiting for DOM update", 2000);

            // Then
            assertThat(result.isWait()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Wait.class);
            ReflectionResult.Wait wait = (ReflectionResult.Wait) result;
            assertThat(wait.waitMs()).isEqualTo(2000);
        }

        @Test
        void shouldReturnReasonForWait() {
            // When
            ReflectionResult result = ReflectionResult.waitFor("Waiting for page load", 1000);

            // Then
            assertThat(result.getReason()).isEqualTo("Waiting for page load");
        }

        @Test
        void shouldReturnEmptyRepairStepsForWait() {
            // When
            ReflectionResult result = ReflectionResult.waitFor("reason", 1000);

            // Then
            assertThat(result.getRepairSteps()).isEmpty();
        }

        @Test
        void shouldNotBeSuccessRetryOrAbort() {
            // When
            ReflectionResult result = ReflectionResult.waitFor("reason", 1000);

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetry()).isFalse();
            assertThat(result.isAbort()).isFalse();
        }
    }

    @Nested
    class AbortResult {

        @Test
        void shouldCreateAbortWithReason() {
            // When
            ReflectionResult result = ReflectionResult.abort("Max retries exceeded");

            // Then
            assertThat(result.isAbort()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Abort.class);
            assertThat(((ReflectionResult.Abort) result).reason()).isEqualTo("Max retries exceeded");
        }

        @Test
        void shouldReturnReasonForAbort() {
            // When
            ReflectionResult result = ReflectionResult.abort("Critical failure");

            // Then
            assertThat(result.getReason()).isEqualTo("Critical failure");
        }

        @Test
        void shouldReturnEmptyRepairStepsForAbort() {
            // When
            ReflectionResult result = ReflectionResult.abort("reason");

            // Then
            assertThat(result.getRepairSteps()).isEmpty();
        }

        @Test
        void shouldNotBeSuccessRetryOrWait() {
            // When
            ReflectionResult result = ReflectionResult.abort("reason");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetry()).isFalse();
            assertThat(result.isWait()).isFalse();
        }
    }

    @Nested
    class SkipResult {

        @Test
        void shouldCreateSkipWithReason() {
            // When
            ReflectionResult result = ReflectionResult.skip("Cookie consent not present");

            // Then
            assertThat(result.isSkip()).isTrue();
            assertThat(result).isInstanceOf(ReflectionResult.Skip.class);
            assertThat(((ReflectionResult.Skip) result).reason()).isEqualTo("Cookie consent not present");
        }

        @Test
        void shouldReturnReasonForSkip() {
            // When
            ReflectionResult result = ReflectionResult.skip("Optional step skipped");

            // Then
            assertThat(result.getReason()).isEqualTo("Optional step skipped");
        }

        @Test
        void shouldReturnEmptyRepairStepsForSkip() {
            // When
            ReflectionResult result = ReflectionResult.skip("reason");

            // Then
            assertThat(result.getRepairSteps()).isEmpty();
        }

        @Test
        void shouldNotBeSuccessRetryWaitOrAbort() {
            // When
            ReflectionResult result = ReflectionResult.skip("reason");

            // Then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.isRetry()).isFalse();
            assertThat(result.isWait()).isFalse();
            assertThat(result.isAbort()).isFalse();
        }
    }

    @Nested
    class PatternMatching {

        @Test
        void shouldAllowPatternMatchingOnAllTypes() {
            // Given
            List<ReflectionResult> results = List.of(
                    ReflectionResult.success("selector"),
                    ReflectionResult.retry("retry", ActionStepFactory.click("btn")),
                    ReflectionResult.waitFor("wait", 1000),
                    ReflectionResult.abort("abort"),
                    ReflectionResult.skip("optional step skipped")
            );

            // When/Then
            for (ReflectionResult result : results) {
                String type = switch (result) {
                    case ReflectionResult.Success s -> "success";
                    case ReflectionResult.Retry r -> "retry";
                    case ReflectionResult.Wait w -> "wait";
                    case ReflectionResult.Abort a -> "abort";
                    case ReflectionResult.Skip s -> "skip";
                };
                assertThat(type).isNotNull();
            }
        }

        @Test
        void shouldExtractDetailsViaPatternMatching() {
            // Given
            ReflectionResult retry = ReflectionResult.retry("Element missing",
                    ActionStepFactory.waitFor("element", 2000));

            // When
            String details = switch (retry) {
                case ReflectionResult.Retry r ->
                        "Retry because: " + r.reason() + ", steps: " + r.repairSteps().size();
                default -> "other";
            };

            // Then
            assertThat(details).isEqualTo("Retry because: Element missing, steps: 1");
        }
    }
}
