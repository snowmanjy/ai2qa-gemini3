package com.ai2qa.application.memory;

import com.ai2qa.application.event.TestRunCompletedSpringEvent;
import com.ai2qa.application.knowledge.KnowledgeService;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.knowledge.SitePattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Listens for test run completion events and triggers memory learning.
 *
 * <p>After each test run completes, the Librarian analyzes the results and
 * extracts reusable insights that are stored in the Global Hippocampus
 * (agent_memory table) for future test runs.
 *
 * <p>This implements the "Learning Loop" pattern:
 * <ol>
 *   <li>Test run completes (success or failure)</li>
 *   <li>Librarian analyzes execution patterns</li>
 *   <li>Insights are extracted and sanitized</li>
 *   <li>New knowledge is appended to memory</li>
 *   <li>Future test runs benefit from accumulated wisdom</li>
 * </ol>
 */
@Component
public class MemoryLearningListener {

    private static final Logger log = LoggerFactory.getLogger(MemoryLearningListener.class);

    private final LibrarianService librarianService;
    private final AgentMemoryService memoryService;
    private final KnowledgeService knowledgeService;
    private final boolean learningEnabled;

    public MemoryLearningListener(
            LibrarianService librarianService,
            AgentMemoryService memoryService,
            KnowledgeService knowledgeService,
            @Value("${ai2qa.memory.learning-enabled:true}") boolean learningEnabled) {
        this.librarianService = librarianService;
        this.memoryService = memoryService;
        this.knowledgeService = knowledgeService;
        this.learningEnabled = learningEnabled;
    }

    /**
     * Handles test run completion events by extracting and storing insights.
     *
     * <p>Runs asynchronously and with low priority (Order 100) to avoid
     * impacting more critical post-run operations like notifications.
     */
    @Async
    @Order(100) // Low priority - run after notifications and other critical listeners
    @EventListener
    public void onTestRunCompleted(TestRunCompletedSpringEvent event) {
        if (!learningEnabled) {
            log.debug("[MEMORY] Learning disabled, skipping insight extraction");
            return;
        }

        TestRun run = event.getTestRun();
        log.info("[MEMORY] Processing test run {} for learning...", run.getId());

        try {
            // Extract insights and patterns using the Librarian
            LibrarianService.ExtractionResult result = librarianService.extractLearnings(run);

            int storedInsights = 0;
            int storedPatterns = 0;

            // Store text insights in the Global Hippocampus (backward compatible)
            for (LibrarianService.ExtractedInsight insight : result.insights()) {
                try {
                    memoryService.appendInsight(insight.contextTag(), insight.insightText());
                    storedInsights++;
                    log.debug("[MEMORY] Stored insight: [{}] {}", insight.contextTag(), insight.insightText());
                } catch (Exception e) {
                    log.warn("[MEMORY] Failed to store insight [{}]: {}",
                            insight.contextTag(), e.getMessage());
                }
            }

            // Store structured patterns in the knowledge base
            for (LibrarianService.ExtractedPattern pattern : result.patterns()) {
                try {
                    Optional<SitePattern> stored = knowledgeService.storePattern(
                            pattern.domain(),
                            pattern.type(),
                            pattern.key(),
                            pattern.value(),
                            run.getId().value(),
                            null // Global visibility
                    );

                    if (stored.isPresent()) {
                        storedPatterns++;
                        log.debug("[MEMORY] Stored pattern: {}/{}/{}",
                                pattern.domain(), pattern.type(), pattern.key());

                        // Store alternative selectors if any
                        for (LibrarianService.AlternativeSelector alt : pattern.alternatives()) {
                            knowledgeService.addSelectorAlternative(
                                    stored.get().id(),
                                    alt.type(),
                                    alt.selector(),
                                    0 // Default priority
                            );
                        }
                    }
                } catch (Exception e) {
                    log.warn("[MEMORY] Failed to store pattern [{}/{}]: {}",
                            pattern.type(), pattern.key(), e.getMessage());
                }
            }

            if (storedInsights == 0 && storedPatterns == 0) {
                log.info("[MEMORY] No learnings extracted from run {}", run.getId());
            } else {
                log.info("[MEMORY] Stored {} insights and {} patterns from run {} to Hippocampus",
                        storedInsights, storedPatterns, run.getId());
            }

        } catch (Exception e) {
            log.error("[MEMORY] Failed to process learning for run {}: {}",
                    run.getId(), e.getMessage(), e);
            // Don't propagate - learning failure should not affect other operations
        }
    }

    /**
     * Manually triggers learning for a specific test run.
     * Useful for re-processing or testing.
     *
     * @param testRun The test run to learn from
     * @return Total number of learnings stored (insights + patterns)
     */
    public int learnFromRun(TestRun testRun) {
        LibrarianService.ExtractionResult result = librarianService.extractLearnings(testRun);

        int stored = 0;

        // Store text insights
        for (LibrarianService.ExtractedInsight insight : result.insights()) {
            try {
                memoryService.appendInsight(insight.contextTag(), insight.insightText());
                stored++;
            } catch (Exception e) {
                log.warn("[MEMORY] Failed to store insight: {}", e.getMessage());
            }
        }

        // Store structured patterns
        for (LibrarianService.ExtractedPattern pattern : result.patterns()) {
            try {
                Optional<SitePattern> storedPattern = knowledgeService.storePattern(
                        pattern.domain(),
                        pattern.type(),
                        pattern.key(),
                        pattern.value(),
                        testRun.getId().value(),
                        null
                );
                if (storedPattern.isPresent()) {
                    stored++;
                }
            } catch (Exception e) {
                log.warn("[MEMORY] Failed to store pattern: {}", e.getMessage());
            }
        }

        return stored;
    }
}
