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
 * JPA entity for persona definition persistence.
 */
@Entity
@Table(name = "persona_definition")
public class PersonaDefinitionEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "temperature", nullable = false)
    private double temperature;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", columnDefinition = "JSONB")
    private String skills;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonaDefinitionEntity() {
        // JPA
    }

    public PersonaDefinitionEntity(
            UUID id,
            String name,
            String displayName,
            double temperature,
            String systemPrompt,
            String skills,
            String source,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.temperature = temperature;
        this.systemPrompt = systemPrompt;
        this.skills = skills;
        this.source = source;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getTemperature() {
        return temperature;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getSkills() {
        return skills;
    }

    public String getSource() {
        return source;
    }

    public boolean isActive() {
        return active;
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

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
