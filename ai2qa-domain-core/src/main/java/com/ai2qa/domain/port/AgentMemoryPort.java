package com.ai2qa.domain.port;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port interface for the Global Memory System (The Hippocampus).
 * 
 * <p>Provides Map-based access to accumulated AI wisdom across test runs.
 */
public interface AgentMemoryPort {

    /**
     * Loads all memory entries as a Map for prompt injection.
     * 
     * @return Map where key=contextTag, value=insightText
     */
    Map<String, String> loadMemoryMap();

    /**
     * Returns list of existing context tags for the Librarian prompt.
     * 
     * @return List of tag names (e.g., ["framework:react", "error:hydration"])
     */
    List<String> getExistingTags();

    /**
     * Appends a new insight to an existing tag, or creates a new entry.
     * Uses pipe-delimited format for accumulation.
     * 
     * @param contextTag The taxonomy key (e.g., "framework:react")
     * @param newInsight The new wisdom to append
     */
    void appendInsight(String contextTag, String newInsight);

    /**
     * Updates the insight text for a tag (used by Janitor for compression).
     * 
     * @param contextTag The taxonomy key
     * @param compressedInsight The new compressed text
     */
    void updateInsight(String contextTag, String compressedInsight);

    /**
     * Finds entries that need compression (exceed threshold length).
     * 
     * @param thresholdLength Maximum allowed length before compression
     * @return List of entries as [tag, insightText] pairs
     */
    List<MemoryEntry> findEntriesNeedingCompression(int thresholdLength);

    /**
     * Deletes a memory entry (used when merging duplicates).
     */
    void deleteEntry(String contextTag);

    /**
     * Simple record for memory entries.
     */
    record MemoryEntry(String contextTag, String insightText) {}
}
