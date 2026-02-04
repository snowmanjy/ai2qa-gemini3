package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for test runs.
 * Uses Hibernate Filter for automatic tenant isolation.
 */
@Filter(name = "tenantFilter")
@Entity
@Table(name = "test_run")
public class TestRunEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "target_url", nullable = false)
    private String targetUrl;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "persona", nullable = false)
    private String persona;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goals", columnDefinition = "jsonb")
    private String goalsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan", columnDefinition = "jsonb")
    private String planJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "executed_steps", columnDefinition = "jsonb")
    private String executedStepsJson;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", columnDefinition = "jsonb")
    private String summaryJson;

    @Column(name = "summary_status")
    private String summaryStatus;

    @Column(name = "notify_on_complete", nullable = false)
    private boolean notifyOnComplete;

    @Column(name = "notification_email")
    private String notificationEmail;

    @Column(name = "execution_mode")
    private String executionMode;

    @Column(name = "agent_id")
    private UUID agentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TestRunEntity() {
        // JPA
    }

    public TestRunEntity(
            UUID id,
            String tenantId,
            String targetUrl,
            String status,
            String persona,
            String goalsJson,
            String planJson,
            String executedStepsJson,
            Instant startedAt,
            Instant completedAt,
            String failureReason,
            String summaryJson,
            String summaryStatus,
            boolean notifyOnComplete,
            String notificationEmail,
            String executionMode,
            UUID agentId,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.targetUrl = targetUrl;
        this.status = status;
        this.persona = persona;
        this.goalsJson = goalsJson;
        this.planJson = planJson;
        this.executedStepsJson = executedStepsJson;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.failureReason = failureReason;
        this.summaryJson = summaryJson;
        this.summaryStatus = summaryStatus;
        this.notifyOnComplete = notifyOnComplete;
        this.notificationEmail = notificationEmail;
        this.executionMode = executionMode;
        this.agentId = agentId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getStatus() {
        return status;
    }

    public String getPersona() {
        return persona;
    }

    public String getGoalsJson() {
        return goalsJson;
    }

    public String getPlanJson() {
        return planJson;
    }

    public String getExecutedStepsJson() {
        return executedStepsJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getSummaryStatus() {
        return summaryStatus;
    }

    public boolean isNotifyOnComplete() {
        return notifyOnComplete;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public UUID getAgentId() {
        return agentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Setters for update operations
    public void setStatus(String status) {
        this.status = status;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public void setExecutedStepsJson(String executedStepsJson) {
        this.executedStepsJson = executedStepsJson;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public void setSummaryStatus(String summaryStatus) {
        this.summaryStatus = summaryStatus;
    }
}
