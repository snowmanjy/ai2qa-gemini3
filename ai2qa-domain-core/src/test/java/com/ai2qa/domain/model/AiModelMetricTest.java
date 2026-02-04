package com.ai2qa.domain.model;

import com.ai2qa.domain.factory.AiModelMetricFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AiModelMetric")
class AiModelMetricTest {

    private static final String TENANT_ID = "tenant_123";
    private static final UUID TEST_RUN_ID = UUID.randomUUID();
    private static final String MODEL_PROVIDER = "vertex-ai";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final Instant NOW = Instant.now();

    @Nested
    @DisplayName("Record accessors")
    class RecordAccessors {

        @Test
        @DisplayName("totalTokens returns sum of input and output tokens")
        void totalTokensReturnsSumOfInputAndOutput() {
            AiModelMetric metric = AiModelMetricFactory.forElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false, NOW);

            assertEquals(150, metric.totalTokens());
        }

        @Test
        @DisplayName("getErrorReason returns empty for successful operation")
        void getErrorReasonReturnsEmptyForSuccess() {
            AiModelMetric metric = AiModelMetricFactory.forElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false, NOW);

            assertTrue(metric.getErrorReason().isEmpty());
        }

        @Test
        @DisplayName("getErrorReason returns reason for failed operation")
        void getErrorReasonReturnsReasonForFailure() {
            AiModelMetric metric = AiModelMetricFactory.forElementFindFailure(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 0, 200, false, "NOT_FOUND", NOW);

            assertEquals(Optional.of("NOT_FOUND"), metric.getErrorReason());
        }

        @Test
        @DisplayName("getTestRunId returns empty when null")
        void getTestRunIdReturnsEmptyWhenNull() {
            AiModelMetric metric = AiModelMetricFactory.forElementFind(
                    TENANT_ID, null, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false, NOW);

            assertTrue(metric.getTestRunId().isEmpty());
        }

        @Test
        @DisplayName("getTestRunId returns value when present")
        void getTestRunIdReturnsValueWhenPresent() {
            AiModelMetric metric = AiModelMetricFactory.forElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false, NOW);

            assertEquals(Optional.of(TEST_RUN_ID), metric.getTestRunId());
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("forElementFind creates successful metric")
        void forElementFindCreatesSuccessfulMetric() {
            AiModelMetric metric = AiModelMetricFactory.forElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false, NOW);

            assertAll(
                    () -> assertNotNull(metric.id()),
                    () -> assertEquals(TENANT_ID, metric.tenantId()),
                    () -> assertEquals(TEST_RUN_ID, metric.testRunId()),
                    () -> assertEquals(MODEL_PROVIDER, metric.modelProvider()),
                    () -> assertEquals(MODEL_NAME, metric.modelName()),
                    () -> assertEquals(AiOperationType.ELEMENT_FIND, metric.operationType()),
                    () -> assertEquals(100, metric.inputTokens()),
                    () -> assertEquals(50, metric.outputTokens()),
                    () -> assertEquals(200, metric.latencyMs()),
                    () -> assertTrue(metric.success()),
                    () -> assertFalse(metric.fallbackUsed()),
                    () -> assertNull(metric.errorReason()),
                    () -> assertEquals(NOW, metric.createdAt())
            );
        }

        @Test
        @DisplayName("forElementFindFailure creates failed metric")
        void forElementFindFailureCreatesFailedMetric() {
            AiModelMetric metric = AiModelMetricFactory.forElementFindFailure(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 0, 200, true, "Element not found", NOW);

            assertAll(
                    () -> assertFalse(metric.success()),
                    () -> assertTrue(metric.fallbackUsed()),
                    () -> assertEquals("Element not found", metric.errorReason())
            );
        }

        @Test
        @DisplayName("forPlanGeneration creates plan generation metric")
        void forPlanGenerationCreatesPlanGenerationMetric() {
            AiModelMetric metric = AiModelMetricFactory.forPlanGeneration(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    500, 300, 1500, true, null, NOW);

            assertEquals(AiOperationType.PLAN_GENERATION, metric.operationType());
        }

        @Test
        @DisplayName("forRepairPlan creates repair plan metric")
        void forRepairPlanCreatesRepairPlanMetric() {
            AiModelMetric metric = AiModelMetricFactory.forRepairPlan(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    200, 100, 800, true, null, NOW);

            assertEquals(AiOperationType.REPAIR_PLAN, metric.operationType());
        }

        @Test
        @DisplayName("create with validation returns empty for blank tenantId")
        void createWithValidationReturnsEmptyForBlankTenantId() {
            Optional<AiModelMetric> result = AiModelMetricFactory.create(
                    "", TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    AiOperationType.ELEMENT_FIND, 100, 50, 200,
                    true, false, null, NOW);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("create with validation returns empty for null provider")
        void createWithValidationReturnsEmptyForNullProvider() {
            Optional<AiModelMetric> result = AiModelMetricFactory.create(
                    TENANT_ID, TEST_RUN_ID, null, MODEL_NAME,
                    AiOperationType.ELEMENT_FIND, 100, 50, 200,
                    true, false, null, NOW);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("create with validation returns metric for valid input")
        void createWithValidationReturnsMetricForValidInput() {
            Optional<AiModelMetric> result = AiModelMetricFactory.create(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    AiOperationType.ELEMENT_FIND, 100, 50, 200,
                    true, false, null, NOW);

            assertTrue(result.isPresent());
            assertEquals(TENANT_ID, result.get().tenantId());
        }
    }
}
