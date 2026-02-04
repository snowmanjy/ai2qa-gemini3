package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for site-specific QA patterns.
 */
@Entity
@Table(name = "qa_site_patterns")
public class SitePatternEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "pattern_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private PatternTypeEnum patternType;

    @Column(name = "pattern_key", nullable = false)
    private String patternKey;

    @Column(name = "pattern_value", nullable = false, columnDefinition = "TEXT")
    private String patternValue;

    @Column(name = "confidence_score", precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "success_count")
    private int successCount;

    @Column(name = "failure_count")
    private int failureCount;

    @Column(name = "avg_duration_ms")
    private Integer avgDurationMs;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by_run_id")
    private UUID createdByRunId;

    @Column(name = "visibility", nullable = false)
    @Enumerated(EnumType.STRING)
    private VisibilityEnum visibility;

    @Column(name = "tenant_id")
    private String tenantId;

    @Version
    @Column(name = "version")
    private int version;

    public enum PatternTypeEnum {
        SELECTOR, TIMING, AUTH, QUIRK
    }

    public enum VisibilityEnum {
        GLOBAL, TENANT, PRIVATE
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public PatternTypeEnum getPatternType() {
        return patternType;
    }

    public void setPatternType(PatternTypeEnum patternType) {
        this.patternType = patternType;
    }

    public String getPatternKey() {
        return patternKey;
    }

    public void setPatternKey(String patternKey) {
        this.patternKey = patternKey;
    }

    public String getPatternValue() {
        return patternValue;
    }

    public void setPatternValue(String patternValue) {
        this.patternValue = patternValue;
    }

    public BigDecimal getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(BigDecimal confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public Integer getAvgDurationMs() {
        return avgDurationMs;
    }

    public void setAvgDurationMs(Integer avgDurationMs) {
        this.avgDurationMs = avgDurationMs;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedByRunId() {
        return createdByRunId;
    }

    public void setCreatedByRunId(UUID createdByRunId) {
        this.createdByRunId = createdByRunId;
    }

    public VisibilityEnum getVisibility() {
        return visibility;
    }

    public void setVisibility(VisibilityEnum visibility) {
        this.visibility = visibility;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
