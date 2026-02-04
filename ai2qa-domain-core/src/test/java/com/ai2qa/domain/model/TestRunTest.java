package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.factory.TestRunIdFactory;
import com.ai2qa.domain.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TestRun aggregate root.
 */
class TestRunTest {

    private TestRun testRun;
    private final Instant fixedTime = Instant.parse("2024-01-01T10:00:00Z");
    private final TestRunId fixedId = TestRunIdFactory.generate();

    @BeforeEach
    void setUp() {
        testRun = TestRun.create(fixedId, "tenant-1", "https://example.com", List.of("Login", "Submit form"),
                fixedTime);
    }

    @Nested
    class Creation {

        @Test
        void shouldCreateWithPendingStatus() {
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.PENDING);
            assertThat(testRun.getId()).isEqualTo(fixedId);
            assertThat(testRun.getTenantId()).isEqualTo("tenant-1");
            assertThat(testRun.getTargetUrl()).isEqualTo("https://example.com");
            assertThat(testRun.getGoals()).containsExactly("Login", "Submit form");
        }

        @Test
        void shouldHaveCreatedAtTimestamp() {
            assertThat(testRun.getCreatedAt()).isEqualTo(fixedTime);
        }

        @Test
        void shouldStartWithEmptyPlannedAndExecutedSteps() {
            assertThat(testRun.getPlannedSteps()).isEmpty();
            assertThat(testRun.getExecutedSteps()).isEmpty();
        }

        @Test
        void shouldHaveNoStartedOrCompletedTime() {
            assertThat(testRun.getStartedAt()).isEmpty();
            assertThat(testRun.getCompletedAt()).isEmpty();
        }
    }

    @Nested
    class Starting {

        @Test
        void shouldStartWithPlan() {
            // Given
            List<ActionStep> plan = List.of(
                    ActionStepFactory.navigate("https://example.com"),
                    ActionStepFactory.click("login button"));

            // When
            Result<Void> result = testRun.start(plan, fixedTime);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.RUNNING);
            assertThat(testRun.getPlannedSteps()).hasSize(2);
            assertThat(testRun.getStartedAt()).isPresent().contains(fixedTime);
        }

        @Test
        void shouldFailToStartNonPendingRun() {
            // Given - start once
            testRun.start(List.of(ActionStepFactory.navigate("url")), fixedTime);

            // When - try to start again
            Result<Void> result = testRun.start(List.of(ActionStepFactory.click("btn")), fixedTime);

            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFailureReason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("Can only start a PENDING test run"));
        }
    }

    @Nested
    class StepExecution {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(
                    ActionStepFactory.navigate("https://example.com"),
                    ActionStepFactory.click("login button")), fixedTime);
        }

        @Test
        void shouldRecordSuccessfulStep() {
            // Given
            ExecutedStep step = ExecutedStep.success(
                    ActionStepFactory.navigate("https://example.com"),
                    null,
                    DomSnapshot.empty(),
                    DomSnapshot.empty(),
                    100,
                    fixedTime);

            // When
            Result<Void> result = testRun.recordStepExecution(step, fixedTime);

            // Then
            assertThat(result.isSuccess()).isTrue();
            assertThat(testRun.getExecutedSteps()).hasSize(1);
        }

        @Test
        void shouldAutoCompleteWhenAllStepsSucceed() {
            // Given
            ActionStep nav = ActionStepFactory.navigate("https://example.com");
            ActionStep click = ActionStepFactory.click("login button");

            // When - execute both steps successfully
            testRun.recordStepExecution(ExecutedStep.success(nav, null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime),
                    fixedTime);
            testRun.recordStepExecution(
                    ExecutedStep.success(click, "btn", DomSnapshot.empty(), DomSnapshot.empty(), 50, fixedTime), fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(testRun.getCompletedAt()).isPresent().contains(fixedTime);
        }

        @Test
        void shouldNotAutoCompleteWhenStepFails() {
            // Given
            ActionStep nav = ActionStepFactory.navigate("https://example.com");
            ActionStep click = ActionStepFactory.click("login button");

            // When - first succeeds, second fails
            testRun.recordStepExecution(ExecutedStep.success(nav, null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime),
                    fixedTime);
            testRun.recordStepExecution(ExecutedStep.failed(click, "Element not found", DomSnapshot.empty(), 0, fixedTime),
                    fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.RUNNING);
        }

        @Test
        void shouldFailToRecordStepWhenNotActive() {
            // Given - complete the test run
            testRun.complete(fixedTime);

            // When
            Result<Void> result = testRun.recordStepExecution(
                    ExecutedStep.success(ActionStepFactory.click("btn"), null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime),
                    fixedTime);

            // Then
            assertThat(result.isFailure()).isTrue();
            assertThat(result.getFailureReason())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("Cannot execute steps"));
        }
    }

    @Nested
    class Completion {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(ActionStepFactory.navigate("url")), fixedTime);
        }

        @Test
        void shouldComplete() {
            // When
            testRun.complete(fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
            assertThat(testRun.getCompletedAt()).isPresent().contains(fixedTime);
        }

        @Test
        void shouldNotCompleteAlreadyTerminal() {
            // Given
            testRun.complete(fixedTime);

            // When
            testRun.complete(fixedTime);

            // Then - status remains the same
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
        }
    }

    @Nested
    class Failure {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(ActionStepFactory.navigate("url")), fixedTime);
        }

        @Test
        void shouldFail() {
            // When
            testRun.fail("Critical error occurred", fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.FAILED);
            assertThat(testRun.getFailureReason()).contains("Critical error occurred");
            assertThat(testRun.getCompletedAt()).isPresent().contains(fixedTime);
        }

    }

    @Nested
    class Cancellation {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(ActionStepFactory.navigate("url")), fixedTime);
        }

        @Test
        void shouldCancel() {
            // When
            testRun.cancel(fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.CANCELLED);
            assertThat(testRun.getCompletedAt()).isPresent().contains(fixedTime);
        }

        @Test
        void shouldNotCancelAlreadyTerminal() {
            // Given
            testRun.complete(fixedTime);

            // When
            testRun.cancel(fixedTime);

            // Then
            assertThat(testRun.getStatus()).isEqualTo(TestRunStatus.COMPLETED);
        }
    }

    @Nested
    class RepairSteps {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(
                    ActionStepFactory.navigate("url"),
                    ActionStepFactory.click("button"),
                    ActionStepFactory.type("input", "value")), fixedTime);
        }

        @Test
        void shouldAddRepairStepsToFront() {
            // Given - execute first step
            testRun.recordStepExecution(ExecutedStep.success(
                    ActionStepFactory.navigate("url"), null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime), fixedTime);

            List<ActionStep> repairSteps = List.of(
                    ActionStepFactory.waitFor("element", 1000),
                    ActionStepFactory.click("retry button"));

            // When
            Result<Void> result = testRun.addRepairSteps(repairSteps);

            // Then
            assertThat(result.isSuccess()).isTrue();
            // Original: nav (executed), click, type
            // After repair: nav, wait, retry-click, click, type
            assertThat(testRun.getPlannedSteps()).hasSize(5);
        }

        @Test
        void shouldFailToAddRepairStepsWhenNotActive() {
            // Given
            testRun.complete(fixedTime);

            // When
            Result<Void> result = testRun.addRepairSteps(List.of(ActionStepFactory.click("btn")));

            // Then
            assertThat(result.isFailure()).isTrue();
        }
    }

    @Nested
    class Queries {

        @BeforeEach
        void startTestRun() {
            testRun.start(List.of(
                    ActionStepFactory.navigate("url"),
                    ActionStepFactory.click("button")), fixedTime);
        }

        @Test
        void shouldReturnNextStep() {
            // When
            var nextStep = testRun.getNextStep();

            // Then
            assertThat(nextStep).isPresent();
            assertThat(nextStep.get().action()).isEqualTo("navigate");
        }

        @Test
        void shouldReturnEmptyWhenNoMoreSteps() {
            // Given - execute all steps
            testRun.recordStepExecution(ExecutedStep.success(
                    ActionStepFactory.navigate("url"), null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime), fixedTime);
            testRun.recordStepExecution(ExecutedStep.success(
                    ActionStepFactory.click("button"), "btn", DomSnapshot.empty(), DomSnapshot.empty(), 50, fixedTime), fixedTime);

            // When
            var nextStep = testRun.getNextStep();

            // Then
            assertThat(nextStep).isEmpty();
        }

        @Test
        void shouldCalculateProgress() {
            // Given - no steps executed yet
            assertThat(testRun.getProgressPercent()).isZero();

            // When - execute first step
            testRun.recordStepExecution(ExecutedStep.success(
                    ActionStepFactory.navigate("url"), null, DomSnapshot.empty(), DomSnapshot.empty(), 100, fixedTime), fixedTime);

            // Then
            assertThat(testRun.getProgressPercent()).isEqualTo(50);
        }

        @Test
        void shouldCalculateDuration() {
            // When
            long duration = testRun.calculateDurationMs(fixedTime.plusSeconds(1));

            // Then
            assertThat(duration).isEqualTo(1000);
        }
    }

    @Nested
    class Reconstitution {

        @Test
        void shouldReconstituteFromPersistence() {
            // Given
            TestRunId id = TestRunIdFactory.generate();
            List<ActionStep> plannedSteps = List.of(ActionStepFactory.navigate("url"));
            List<ExecutedStep> executedSteps = List.of();

            // When
            TestRun reconstituted = TestRun.reconstitute(
                    id,
                    "tenant-1",
                    "https://example.com",
                    List.of("goal1"),
                    TestPersona.STANDARD,
                    null, // cookiesJson
                    false, // notifyOnComplete
                    null, // notificationEmail
                    TestRunStatus.RUNNING,
                    testRun.getCreatedAt(),
                    testRun.getCreatedAt(),
                    null, // completedAt
                    plannedSteps,
                    executedSteps,
                    null, // failureReason
                    null); // summary

            // Then
            assertThat(reconstituted.getId()).isEqualTo(id);
            assertThat(reconstituted.getStatus()).isEqualTo(TestRunStatus.RUNNING);
            assertThat(reconstituted.getPlannedSteps()).isEqualTo(plannedSteps);
        }
    }
}
