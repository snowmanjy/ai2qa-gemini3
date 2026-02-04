package com.ai2qa.application.metrics;

import com.ai2qa.domain.model.AiModelMetric;
import com.ai2qa.domain.model.AiOperationType;
import com.ai2qa.domain.repository.AiModelMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AiMetricsService")
class AiMetricsServiceTest {

    private static final String TENANT_ID = "tenant_123";
    private static final UUID TEST_RUN_ID = UUID.randomUUID();
    private static final String MODEL_PROVIDER = "vertex-ai";
    private static final String MODEL_NAME = "gemini-2.0-flash";
    private static final Instant FIXED_TIME = Instant.parse("2026-01-14T12:00:00Z");

    private AiModelMetricRepository repository;
    private Clock clock;
    private AiMetricsService service;

    @BeforeEach
    void setUp() {
        repository = mock(AiModelMetricRepository.class);
        clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        service = new AiMetricsService(repository, clock);

        when(repository.save(any(AiModelMetric.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("recordElementFind")
    class RecordElementFind {

        @Test
        @DisplayName("saves successful element find metric")
        void savesSuccessfulElementFindMetric() {
            service.recordElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false);

            ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
            verify(repository).save(captor.capture());

            AiModelMetric saved = captor.getValue();
            assertAll(
                    () -> assertEquals(TENANT_ID, saved.tenantId()),
                    () -> assertEquals(TEST_RUN_ID, saved.testRunId()),
                    () -> assertEquals(MODEL_PROVIDER, saved.modelProvider()),
                    () -> assertEquals(MODEL_NAME, saved.modelName()),
                    () -> assertEquals(AiOperationType.ELEMENT_FIND, saved.operationType()),
                    () -> assertEquals(100, saved.inputTokens()),
                    () -> assertEquals(50, saved.outputTokens()),
                    () -> assertEquals(200, saved.latencyMs()),
                    () -> assertTrue(saved.success()),
                    () -> assertFalse(saved.fallbackUsed()),
                    () -> assertEquals(FIXED_TIME, saved.createdAt())
            );
        }

        @Test
        @DisplayName("records fallback usage")
        void recordsFallbackUsage() {
            service.recordElementFind(
                    TENANT_ID, TEST_RUN_ID, "anthropic", "claude-sonnet-4",
                    100, 50, 300, true);

            ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
            verify(repository).save(captor.capture());

            AiModelMetric saved = captor.getValue();
            assertAll(
                    () -> assertEquals("anthropic", saved.modelProvider()),
                    () -> assertTrue(saved.fallbackUsed())
            );
        }
    }

    @Nested
    @DisplayName("recordElementFindFailure")
    class RecordElementFindFailure {

        @Test
        @DisplayName("saves failed element find metric with error reason")
        void savesFailedElementFindMetricWithErrorReason() {
            service.recordElementFindFailure(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 0, 200, false, "NOT_FOUND");

            ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
            verify(repository).save(captor.capture());

            AiModelMetric saved = captor.getValue();
            assertAll(
                    () -> assertFalse(saved.success()),
                    () -> assertEquals("NOT_FOUND", saved.errorReason())
            );
        }
    }

    @Nested
    @DisplayName("recordPlanGeneration")
    class RecordPlanGeneration {

        @Test
        @DisplayName("saves plan generation metric")
        void savesPlanGenerationMetric() {
            service.recordPlanGeneration(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    500, 300, 1500, true, null);

            ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
            verify(repository).save(captor.capture());

            AiModelMetric saved = captor.getValue();
            assertEquals(AiOperationType.PLAN_GENERATION, saved.operationType());
        }
    }

    @Nested
    @DisplayName("recordRepairPlan")
    class RecordRepairPlan {

        @Test
        @DisplayName("saves repair plan metric")
        void savesRepairPlanMetric() {
            service.recordRepairPlan(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    200, 100, 800, true, null);

            ArgumentCaptor<AiModelMetric> captor = ArgumentCaptor.forClass(AiModelMetric.class);
            verify(repository).save(captor.capture());

            AiModelMetric saved = captor.getValue();
            assertEquals(AiOperationType.REPAIR_PLAN, saved.operationType());
        }
    }

    @Nested
    @DisplayName("getFallbackRate")
    class GetFallbackRate {

        @Test
        @DisplayName("returns 0 when no metrics exist")
        void returnsZeroWhenNoMetricsExist() {
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "vertex-ai")).thenReturn(0L);
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "anthropic")).thenReturn(0L);
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "openai")).thenReturn(0L);

            double rate = service.getFallbackRate(TENANT_ID);

            assertEquals(0.0, rate);
        }

        @Test
        @DisplayName("calculates fallback rate correctly")
        void calculatesFallbackRateCorrectly() {
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "vertex-ai")).thenReturn(80L);
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "anthropic")).thenReturn(20L);
            when(repository.countByTenantIdAndModelProvider(TENANT_ID, "openai")).thenReturn(0L);
            when(repository.countFallbacksByTenantId(TENANT_ID)).thenReturn(20L);

            double rate = service.getFallbackRate(TENANT_ID);

            assertEquals(20.0, rate, 0.01); // 20 fallbacks out of 100 total = 20%
        }
    }

    @Nested
    @DisplayName("getMetricsForTenant")
    class GetMetricsForTenant {

        @Test
        @DisplayName("delegates to repository")
        void delegatesToRepository() {
            List<AiModelMetric> expected = List.of();
            when(repository.findByTenantId(TENANT_ID)).thenReturn(expected);

            List<AiModelMetric> result = service.getMetricsForTenant(TENANT_ID);

            assertSame(expected, result);
            verify(repository).findByTenantId(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("getMetricsForTestRun")
    class GetMetricsForTestRun {

        @Test
        @DisplayName("delegates to repository")
        void delegatesToRepository() {
            List<AiModelMetric> expected = List.of();
            when(repository.findByTestRunId(TEST_RUN_ID)).thenReturn(expected);

            List<AiModelMetric> result = service.getMetricsForTestRun(TEST_RUN_ID);

            assertSame(expected, result);
            verify(repository).findByTestRunId(TEST_RUN_ID);
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("does not throw when repository fails")
        void doesNotThrowWhenRepositoryFails() {
            when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            assertDoesNotThrow(() -> service.recordElementFind(
                    TENANT_ID, TEST_RUN_ID, MODEL_PROVIDER, MODEL_NAME,
                    100, 50, 200, false));
        }
    }
}
