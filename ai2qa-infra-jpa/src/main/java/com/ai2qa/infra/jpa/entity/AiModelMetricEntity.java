package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for AI model metrics persistence.
 */
@Entity
@Table(name = "ai_model_metrics")
public class AiModelMetricEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "test_run_id")
    private UUID testRunId;

    @Column(name = "model_provider", nullable = false)
    private String modelProvider;

    @Column(name = "model_name")
    private String modelName;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private AiOperationTypeEntity operationType;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "error_reason")
    private String errorReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiModelMetricEntity() {
        // JPA
    }

    public AiModelMetricEntity(
            UUID id,
            String tenantId,
            UUID testRunId,
            String modelProvider,
            String modelName,
            AiOperationTypeEntity operationType,
            int inputTokens,
            int outputTokens,
            int latencyMs,
            boolean success,
            boolean fallbackUsed,
            String errorReason,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.testRunId = testRunId;
        this.modelProvider = modelProvider;
        this.modelName = modelName;
        this.operationType = operationType;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.success = success;
        this.fallbackUsed = fallbackUsed;
        this.errorReason = errorReason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getTestRunId() {
        return testRunId;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public String getModelName() {
        return modelName;
    }

    public AiOperationTypeEntity getOperationType() {
        return operationType;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public String getErrorReason() {
        return errorReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * JPA-friendly enum for AiOperationType.
     */
    public enum AiOperationTypeEntity {
        ELEMENT_FIND,
        PLAN_GENERATION,
        REPAIR_PLAN,
        REFLECTION
    }
}
