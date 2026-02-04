package com.ai2qa.application.memory;

import com.ai2qa.application.port.ChatClientPort;
import com.ai2qa.domain.port.AgentMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Weekly scheduled task that compresses long memory entries.
 *
 * <p>Runs every Monday at 5:00 AM PST to:
 * <ol>
 *   <li>Find entries exceeding 500 chars</li>
 *   <li>Use AI to summarize and compress them</li>
 *   <li>Detect and suggest duplicate key merges</li>
 * </ol>
 *
 * <p>Uses a 30-second timeout for AI calls (fail fast for simple prompts).
 * Includes a warmup request to establish connection before processing.
 */
@Component
public class MemoryJanitorTask {

    private static final Logger log = LoggerFactory.getLogger(MemoryJanitorTask.class);
    private static final int COMPRESSION_THRESHOLD = 500;
    private static final long JANITOR_TIMEOUT_SECONDS = 30L; // Fail fast for simple prompts

    private final AgentMemoryService memoryService;
    private final ChatClientPort chatClient;

    public MemoryJanitorTask(
            AgentMemoryService memoryService,
            @Qualifier("plannerChatPort") ChatClientPort chatClient) {
        this.memoryService = memoryService;
        this.chatClient = chatClient;
    }

    /**
     * Runs weekly on Monday at 5:00 AM PST to compress memory entries.
     *
     * <p>Timezone is explicitly set to avoid UTC/PST confusion.
     */
    @Scheduled(cron = "0 0 5 * * MON", zone = "America/Los_Angeles")
    public void compressMemory() {
        log.info("[JANITOR] Starting weekly memory compression...");

        try {
            // 1. Find entries needing compression
            List<AgentMemoryPort.MemoryEntry> longEntries = memoryService.findEntriesNeedingCompression(COMPRESSION_THRESHOLD);
            log.info("[JANITOR] Found {} entries exceeding {} chars", longEntries.size(), COMPRESSION_THRESHOLD);

            if (longEntries.isEmpty()) {
                log.info("[JANITOR] No entries need compression. Skipping AI calls.");
                return;
            }

            // 2. Warmup: Send a minimal request to establish connection and avoid cold start issues
            if (!warmupAiConnection()) {
                log.warn("[JANITOR] AI warmup failed. Aborting compression to avoid stuck requests.");
                return;
            }

            // 3. Compress each entry
            int compressed = 0;
            for (AgentMemoryPort.MemoryEntry entry : longEntries) {
                if (compressEntry(entry)) {
                    compressed++;
                }
            }

            // 4. Only check for duplicates if we actually compressed something
            if (compressed > 0) {
                detectDuplicateKeys();
            }

            log.info("[JANITOR] Memory compression completed. Compressed {}/{} entries.",
                    compressed, longEntries.size());

        } catch (Exception e) {
            log.error("[JANITOR] Memory compression failed", e);
        }
    }

    /**
     * Sends a minimal warmup request to establish AI connection.
     *
     * <p>This helps avoid cold start latency issues and validates the connection
     * before processing real entries.
     *
     * @return true if warmup succeeded, false otherwise
     */
    private boolean warmupAiConnection() {
        log.debug("[JANITOR] Warming up AI connection...");
        try {
            String response = chatClient.callWithTimeout(
                    null,
                    "Respond with exactly: OK",
                    JANITOR_TIMEOUT_SECONDS
            );
            if (response != null && response.contains("OK")) {
                log.debug("[JANITOR] AI warmup successful");
                return true;
            }
            log.warn("[JANITOR] AI warmup returned unexpected response: {}", response);
            return false;
        } catch (Exception e) {
            log.error("[JANITOR] AI warmup failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean compressEntry(AgentMemoryPort.MemoryEntry entry) {
        String prompt = String.format("""
            You are the Memory Curator for an AI testing system.

            The following wisdom for the tag **"%s"** has grown too long.
            Summarize and merge these insights into a concise, actionable summary.
            Remove duplicates and keep only the most valuable patterns.

            Current Wisdom:
            %s

            Output ONLY the compressed wisdom (no explanation, no formatting).
            Keep it under 400 characters.
            """, entry.contextTag(), entry.insightText());

        try {
            // Use shorter timeout for simple compression tasks (fail fast)
            String compressed = chatClient.callWithTimeout(null, prompt, JANITOR_TIMEOUT_SECONDS);

            if (compressed != null && !compressed.isBlank() && compressed.length() < entry.insightText().length()) {
                memoryService.updateInsight(entry.contextTag(), compressed.trim());
                log.info("[JANITOR] Compressed '{}': {} -> {} chars",
                        entry.contextTag(), entry.insightText().length(), compressed.length());
                return true;
            }

        } catch (Exception e) {
            log.warn("[JANITOR] Failed to compress '{}': {}", entry.contextTag(), e.getMessage());
        }
        return false;
    }

    private void detectDuplicateKeys() {
        List<String> tags = memoryService.getExistingTags();
        if (tags.size() < 2) return;

        String prompt = String.format("""
            You are analyzing a knowledge base taxonomy.

            Current Tags:
            %s

            Are there any tags that mean the same thing and should be merged?
            For example: "react" and "reactjs" or "error:500" and "http:500"

            If duplicates exist, respond with:
            MERGE: [tag1] -> [tag2]

            If no duplicates, respond with:
            NO_DUPLICATES
            """, tags);

        try {
            // Use shorter timeout for simple analysis tasks (fail fast)
            String response = chatClient.callWithTimeout(null, prompt, JANITOR_TIMEOUT_SECONDS);

            if (response != null && response.contains("MERGE:")) {
                log.warn("[JANITOR] Duplicate keys detected: {}", response);
                // Future: Auto-merge logic could go here
            }

        } catch (Exception e) {
            log.warn("[JANITOR] Duplicate detection failed: {}", e.getMessage());
        }
    }

    /**
     * Manual trigger for testing (not scheduled).
     */
    public void runManually() {
        compressMemory();
    }
}
