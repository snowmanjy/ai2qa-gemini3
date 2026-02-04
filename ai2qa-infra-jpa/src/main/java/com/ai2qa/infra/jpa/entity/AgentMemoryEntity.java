package com.ai2qa.infra.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for global AI memory persistence.
 * 
 * <p>Represents a key-value pair in the agent's "Hippocampus" where:
 * <ul>
 *   <li>Key = contextTag (e.g., "framework:react", "error:hydration")</li>
 *   <li>Value = insightText (accumulated wisdom, pipe-delimited)</li>
 * </ul>
 */
@Entity
@Table(name = "agent_memory")
public class AgentMemoryEntity {

    @Id
    @Column(name = "context_tag", nullable = false, length = 255)
    private String contextTag;

    @Column(name = "insight_text", nullable = false, columnDefinition = "TEXT")
    private String insightText;

    @Column(name = "last_updated_at", nullable = false)
    private Instant lastUpdatedAt;

    protected AgentMemoryEntity() {
        // JPA
    }

    public AgentMemoryEntity(String contextTag, String insightText, Instant lastUpdatedAt) {
        this.contextTag = contextTag;
        this.insightText = insightText;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public static AgentMemoryEntity create(String contextTag, String insightText) {
        return new AgentMemoryEntity(contextTag, insightText, Instant.now());
    }

    public String getContextTag() {
        return contextTag;
    }

    public String getInsightText() {
        return insightText;
    }

    public Instant getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setInsightText(String insightText) {
        this.insightText = insightText;
        this.lastUpdatedAt = Instant.now();
    }

    /**
     * Appends a new insight to the existing text using pipe delimiter.
     */
    public void appendInsight(String newInsight) {
        if (this.insightText == null || this.insightText.isBlank()) {
            this.insightText = newInsight;
        } else {
            this.insightText = this.insightText + " | " + newInsight;
        }
        this.lastUpdatedAt = Instant.now();
    }
}
