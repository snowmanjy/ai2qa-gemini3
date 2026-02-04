package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for selector alternatives.
 */
@Entity
@Table(name = "qa_selector_alternatives")
public class SelectorAlternativeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "site_pattern_id", nullable = false)
    private UUID sitePatternId;

    @Column(name = "selector_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SelectorTypeEnum selectorType;

    @Column(name = "selector_value", nullable = false, columnDefinition = "TEXT")
    private String selectorValue;

    @Column(name = "priority")
    private int priority;

    @Column(name = "success_rate", precision = 3, scale = 2)
    private BigDecimal successRate;

    @Column(name = "success_count")
    private int successCount;

    @Column(name = "failure_count")
    private int failureCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum SelectorTypeEnum {
        CSS, XPATH, TEXT, ARIA, DATA_TESTID
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSitePatternId() {
        return sitePatternId;
    }

    public void setSitePatternId(UUID sitePatternId) {
        this.sitePatternId = sitePatternId;
    }

    public SelectorTypeEnum getSelectorType() {
        return selectorType;
    }

    public void setSelectorType(SelectorTypeEnum selectorType) {
        this.selectorType = selectorType;
    }

    public String getSelectorValue() {
        return selectorValue;
    }

    public void setSelectorValue(String selectorValue) {
        this.selectorValue = selectorValue;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(BigDecimal successRate) {
        this.successRate = successRate;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
