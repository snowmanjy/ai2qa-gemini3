package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for skill persistence.
 */
@Entity
@Table(name = "skill")
public class SkillEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "instructions", nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patterns", columnDefinition = "JSONB")
    private String patterns;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SkillEntity() {
        // JPA
    }

    public SkillEntity(
            UUID id,
            String name,
            String instructions,
            String patterns,
            String category,
            String status,
            String sourceUrl,
            String sourceHash,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.instructions = instructions;
        this.patterns = patterns;
        this.category = category;
        this.status = status;
        this.sourceUrl = sourceUrl;
        this.sourceHash = sourceHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getPatterns() {
        return patterns;
    }

    public String getCategory() {
        return category;
    }

    public String getStatus() {
        return status;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public void setPatterns(String patterns) {
        this.patterns = patterns;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setSourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
