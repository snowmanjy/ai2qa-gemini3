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
 * JPA entity for knowledge access logging (metering).
 */
@Entity
@Table(name = "qa_knowledge_access_log")
public class KnowledgeAccessLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "access_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccessTypeEnum accessType;

    @Column(name = "domain")
    private String domain;

    @Column(name = "patterns_accessed")
    private int patternsAccessed;

    @Column(name = "patterns_contributed")
    private int patternsContributed;

    @Column(name = "credits_charged", precision = 10, scale = 4)
    private BigDecimal creditsCharged;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    public enum AccessTypeEnum {
        RENT, LEARN
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public AccessTypeEnum getAccessType() {
        return accessType;
    }

    public void setAccessType(AccessTypeEnum accessType) {
        this.accessType = accessType;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getPatternsAccessed() {
        return patternsAccessed;
    }

    public void setPatternsAccessed(int patternsAccessed) {
        this.patternsAccessed = patternsAccessed;
    }

    public int getPatternsContributed() {
        return patternsContributed;
    }

    public void setPatternsContributed(int patternsContributed) {
        this.patternsContributed = patternsContributed;
    }

    public BigDecimal getCreditsCharged() {
        return creditsCharged;
    }

    public void setCreditsCharged(BigDecimal creditsCharged) {
        this.creditsCharged = creditsCharged;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Instant accessedAt) {
        this.accessedAt = accessedAt;
    }
}
