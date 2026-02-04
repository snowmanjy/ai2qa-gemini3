package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.model.AiModelMetric;
import com.ai2qa.domain.model.AiOperationType;
import com.ai2qa.domain.repository.AiModelMetricRepository;
import com.ai2qa.infra.jpa.entity.AiModelMetricEntity;
import com.ai2qa.infra.jpa.entity.AiModelMetricEntity.AiOperationTypeEntity;
import com.ai2qa.infra.jpa.repository.AiModelMetricJpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA implementation of AiModelMetricRepository.
 */
@Repository
public class AiModelMetricRepositoryImpl implements AiModelMetricRepository {

    private final AiModelMetricJpaRepository jpaRepository;

    public AiModelMetricRepositoryImpl(AiModelMetricJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AiModelMetric save(AiModelMetric metric) {
        AiModelMetricEntity entity = toEntity(metric);
        AiModelMetricEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void saveAll(List<AiModelMetric> metrics) {
        List<AiModelMetricEntity> entities = metrics.stream()
                .map(this::toEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<AiModelMetric> findByTenantId(String tenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AiModelMetric> findByTestRunId(UUID testRunId) {
        return jpaRepository.findByTestRunId(testRunId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<AiModelMetric> findByCreatedAtBetween(Instant start, Instant end) {
        return jpaRepository.findByCreatedAtBetween(start, end).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByTenantIdAndModelProvider(String tenantId, String modelProvider) {
        return jpaRepository.countByTenantIdAndModelProvider(tenantId, modelProvider);
    }

    @Override
    public long countFallbacksByTenantId(String tenantId) {
        return jpaRepository.countByTenantIdAndFallbackUsed(tenantId, true);
    }

    @Override
    public long countInRange(Instant start, Instant end) {
        return jpaRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    public long countSuccessInRange(Instant start, Instant end) {
        return jpaRepository.countByCreatedAtBetweenAndSuccess(start, end, true);
    }

    @Override
    public long countFallbacksInRange(Instant start, Instant end) {
        return jpaRepository.countByCreatedAtBetweenAndFallbackUsed(start, end, true);
    }

    @Override
    public List<ModelAggregation> aggregateByModelInRange(Instant start, Instant end) {
        return jpaRepository.aggregateByModelInRange(start, end).stream()
                .map(row -> new ModelAggregation(
                        (String) row[0],
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).doubleValue()
                ))
                .toList();
    }

    @Override
    public List<OperationAggregation> aggregateByOperationInRange(Instant start, Instant end) {
        return jpaRepository.aggregateByOperationTypeInRange(start, end).stream()
                .map(row -> new OperationAggregation(
                        row[0].toString(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).doubleValue()
                ))
                .toList();
    }

    @Override
    public List<Integer> findLatenciesInRange(Instant start, Instant end) {
        return jpaRepository.findLatenciesInRange(start, end);
    }

    @Override
    public List<HourlyAggregation> getHourlyAggregates(Instant start, Instant end) {
        return jpaRepository.getHourlyAggregates(start, end).stream()
                .map(row -> new HourlyAggregation(
                        ((java.sql.Timestamp) row[0]).toInstant(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        ((Number) row[4]).longValue()
                ))
                .toList();
    }

    private AiModelMetricEntity toEntity(AiModelMetric metric) {
        return new AiModelMetricEntity(
                metric.id(),
                metric.tenantId(),
                metric.testRunId(),
                metric.modelProvider(),
                metric.modelName(),
                toEntityOperationType(metric.operationType()),
                metric.inputTokens(),
                metric.outputTokens(),
                metric.latencyMs(),
                metric.success(),
                metric.fallbackUsed(),
                metric.errorReason(),
                metric.createdAt());
    }

    private AiModelMetric toDomain(AiModelMetricEntity entity) {
        return new AiModelMetric(
                entity.getId(),
                entity.getTenantId(),
                entity.getTestRunId(),
                entity.getModelProvider(),
                entity.getModelName(),
                toDomainOperationType(entity.getOperationType()),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getLatencyMs(),
                entity.isSuccess(),
                entity.isFallbackUsed(),
                entity.getErrorReason(),
                entity.getCreatedAt());
    }

    private AiOperationTypeEntity toEntityOperationType(AiOperationType type) {
        return switch (type) {
            case ELEMENT_FIND -> AiOperationTypeEntity.ELEMENT_FIND;
            case PLAN_GENERATION -> AiOperationTypeEntity.PLAN_GENERATION;
            case REPAIR_PLAN -> AiOperationTypeEntity.REPAIR_PLAN;
            case REFLECTION -> AiOperationTypeEntity.REFLECTION;
        };
    }

    private AiOperationType toDomainOperationType(AiOperationTypeEntity entity) {
        return switch (entity) {
            case ELEMENT_FIND -> AiOperationType.ELEMENT_FIND;
            case PLAN_GENERATION -> AiOperationType.PLAN_GENERATION;
            case REPAIR_PLAN -> AiOperationType.REPAIR_PLAN;
            case REFLECTION -> AiOperationType.REFLECTION;
        };
    }
}
