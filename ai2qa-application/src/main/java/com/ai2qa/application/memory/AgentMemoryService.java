package com.ai2qa.application.memory;

import com.ai2qa.domain.port.AgentMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for managing the AI agent's global memory (The Hippocampus).
 * 
 * <p>Provides Map-based access to accumulated wisdom across test runs.
 */
@Service
public class AgentMemoryService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryService.class);

    private final AgentMemoryPort memoryPort;

    public AgentMemoryService(AgentMemoryPort memoryPort) {
        this.memoryPort = memoryPort;
    }

    /**
     * Loads all memory entries as a Map for prompt injection.
     * 
     * @return Map where key=contextTag, value=insightText
     */
    public Map<String, String> loadMemoryMap() {
        return memoryPort.loadMemoryMap();
    }

    /**
     * Returns list of existing context tags for the Librarian prompt.
     * 
     * @return List of tag names (e.g., ["framework:react", "error:hydration"])
     */
    public List<String> getExistingTags() {
        return memoryPort.getExistingTags();
    }

    /**
     * Appends a new insight to an existing tag, or creates a new entry.
     * Uses pipe-delimited format for accumulation.
     * 
     * @param contextTag The taxonomy key (e.g., "framework:react")
     * @param newInsight The new wisdom to append
     */
    public void appendInsight(String contextTag, String newInsight) {
        memoryPort.appendInsight(contextTag, newInsight);
    }

    /**
     * Updates the insight text for a tag (used by Janitor for compression).
     * 
     * @param contextTag The taxonomy key
     * @param compressedInsight The new compressed text
     */
    public void updateInsight(String contextTag, String compressedInsight) {
        memoryPort.updateInsight(contextTag, compressedInsight);
    }

    /**
     * Finds entries that need compression (exceed threshold length).
     */
    public List<AgentMemoryPort.MemoryEntry> findEntriesNeedingCompression(int thresholdLength) {
        return memoryPort.findEntriesNeedingCompression(thresholdLength);
    }

    /**
     * Deletes a memory entry (used when merging duplicates).
     */
    public void deleteEntry(String contextTag) {
        memoryPort.deleteEntry(contextTag);
    }
}
