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
 * JPA entity for security audit log persistence.
 *
 * <p>Tracks all URL validation decisions for security monitoring:
 * - ALLOWED: Request passed validation
 * - BLOCKED: Request blocked due to security rules
 * - RATE_LIMITED: Request blocked due to rate limits
 */
@Entity
@Table(name = "security_audit_log")
public class SecurityAuditLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "target_url", length = 2048)
    private String targetUrl;

    @Column(name = "target_domain")
    private String targetDomain;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private Decision decision;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id")
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SecurityAuditLogEntity() {
        // JPA
    }

    public SecurityAuditLogEntity(
            UUID id,
            String tenantId,
            String clientIp,
            String targetUrl,
            String targetDomain,
            Decision decision,
            String blockReason,
            Integer riskScore,
            String userAgent,
            String requestId,
            Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientIp = clientIp;
        this.targetUrl = targetUrl;
        this.targetDomain = targetDomain;
        this.decision = decision;
        this.blockReason = blockReason;
        this.riskScore = riskScore;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    // Getters

    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public String getTargetDomain() {
        return targetDomain;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Security decision types.
     */
    public enum Decision {
        ALLOWED,
        BLOCKED,
        RATE_LIMITED
    }
}
