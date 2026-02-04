package com.ai2qa.infra.jpa.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for selector cache.
 * Uses Hibernate Filter for automatic tenant isolation.
 */
@Filter(name = "tenantFilter")
@Entity
@Table(name = "selector_cache")
public class SelectorCacheEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "goal_hash", nullable = false, length = 64)
    private String goalHash;

    @Column(name = "url_pattern", nullable = false, length = 512)
    private String urlPattern;

    @Column(name = "selector_value", nullable = false)
    private byte[] selectorValue;

    @Column(name = "element_description", length = 512)
    private String elementDescription;

    @Column(name = "success_count")
    private int successCount = 1;

    @Column(name = "failure_count")
    private int failureCount = 0;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SelectorCacheEntity() {
        // JPA
    }

    public SelectorCacheEntity(
            UUID id,
            String tenantId,
            String goalHash,
            String urlPattern,
            byte[] selectorValue,
            String elementDescription,
            Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.goalHash = goalHash;
        this.urlPattern = urlPattern;
        this.selectorValue = selectorValue;
        this.elementDescription = elementDescription;
        this.successCount = 1;
        this.failureCount = 0;
        this.lastUsedAt = now;
        this.lastSuccessAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getGoalHash() {
        return goalHash;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public byte[] getSelectorValue() {
        return selectorValue;
    }

    public String getElementDescription() {
        return elementDescription;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Update methods
    public void incrementSuccessCount(Instant now) {
        this.successCount++;
        this.lastUsedAt = now;
        this.lastSuccessAt = now;
        this.updatedAt = now;
    }

    public void incrementFailureCount(Instant now) {
        this.failureCount++;
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    public void updateSelector(byte[] newSelectorValue, String newDescription, Instant now) {
        this.selectorValue = newSelectorValue;
        this.elementDescription = newDescription;
        this.successCount = 1;
        this.failureCount = 0;
        this.lastUsedAt = now;
        this.lastSuccessAt = now;
        this.updatedAt = now;
    }
}
