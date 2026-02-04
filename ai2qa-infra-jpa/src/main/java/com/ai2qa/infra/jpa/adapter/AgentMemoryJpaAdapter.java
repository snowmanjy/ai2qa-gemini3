package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.port.AgentMemoryPort;
import com.ai2qa.infra.jpa.entity.AgentMemoryEntity;
import com.ai2qa.infra.jpa.repository.AgentMemoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JPA adapter implementing the AgentMemoryPort.
 *
 * <p>Includes duplicate detection to avoid storing redundant insights.
 */
@Component
public class AgentMemoryJpaAdapter implements AgentMemoryPort {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryJpaAdapter.class);

    // Similarity threshold - if > 70% of words match, consider it duplicate
    private static final double SIMILARITY_THRESHOLD = 0.70;

    // Minimum words to consider for similarity check
    private static final int MIN_WORDS_FOR_SIMILARITY = 5;

    private final AgentMemoryJpaRepository repository;

    public AgentMemoryJpaAdapter(AgentMemoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> loadMemoryMap() {
        List<AgentMemoryEntity> entries = repository.findAllByOrderByLastUpdatedAtDesc();

        return entries.stream()
                .collect(Collectors.toMap(
                        AgentMemoryEntity::getContextTag,
                        AgentMemoryEntity::getInsightText
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getExistingTags() {
        return repository.findAll().stream()
                .map(AgentMemoryEntity::getContextTag)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void appendInsight(String contextTag, String newInsight) {
        if (contextTag == null || contextTag.isBlank() || newInsight == null || newInsight.isBlank()) {
            log.warn("Ignoring empty memory update: tag={}, insight={}", contextTag, newInsight);
            return;
        }

        // Normalize the tag (convert hyphens to underscores for consistency)
        String normalizedTag = normalizeTag(contextTag);

        // Try to find existing entry with normalized tag
        AgentMemoryEntity entity = repository.findById(normalizedTag)
                .or(() -> repository.findById(contextTag))  // Fallback to original tag
                .orElse(null);

        if (entity == null) {
            // Check if there's a similar tag with different format (e.g., hyphen vs underscore)
            entity = findSimilarTagEntity(normalizedTag);
        }

        if (entity == null) {
            entity = AgentMemoryEntity.create(normalizedTag, newInsight);
            log.info("Creating new memory entry: tag={}", normalizedTag);
        } else {
            // Check for duplicate before appending
            if (isDuplicateInsight(entity.getInsightText(), newInsight)) {
                log.debug("Skipping duplicate insight for tag={}", normalizedTag);
                return;
            }
            entity.appendInsight(newInsight);
            log.info("Appending to memory: tag={}, totalLength={}", normalizedTag, entity.getInsightText().length());
        }

        repository.save(entity);
    }

    /**
     * Normalizes a tag to consistent format.
     * Converts hyphens to underscores and removes consecutive underscores.
     */
    private String normalizeTag(String tag) {
        return tag.toLowerCase()
                .replace("-", "_")
                .replaceAll("_+", "_");
    }

    /**
     * Finds an entity with a similar tag format.
     * For example, finds "wait:page_load" when looking for "wait:page-load".
     */
    private AgentMemoryEntity findSimilarTagEntity(String normalizedTag) {
        // Extract category (before colon)
        int colonIdx = normalizedTag.indexOf(':');
        if (colonIdx < 0) return null;

        String category = normalizedTag.substring(0, colonIdx);

        // Look for any existing tags in the same category
        return repository.findAll().stream()
                .filter(e -> normalizeTag(e.getContextTag()).equals(normalizedTag))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if the new insight is substantially similar to existing content.
     *
     * <p>Uses word-level Jaccard similarity. If more than 70% of words match,
     * the insight is considered a duplicate.
     */
    private boolean isDuplicateInsight(String existingText, String newInsight) {
        Set<String> existingWords = extractWords(existingText);
        Set<String> newWords = extractWords(newInsight);

        // Skip similarity check for very short insights
        if (newWords.size() < MIN_WORDS_FOR_SIMILARITY) {
            return false;
        }

        // Calculate Jaccard similarity
        Set<String> intersection = new HashSet<>(existingWords);
        intersection.retainAll(newWords);

        // If most of the new insight's words are already present, it's a duplicate
        double similarity = (double) intersection.size() / newWords.size();

        if (similarity >= SIMILARITY_THRESHOLD) {
            log.debug("Duplicate detected: {:.0f}% word overlap ({} of {} words)",
                    similarity * 100, intersection.size(), newWords.size());
            return true;
        }

        return false;
    }

    /**
     * Extracts significant words from text for similarity comparison.
     * Filters out common stop words and short words.
     */
    private Set<String> extractWords(String text) {
        if (text == null) return Set.of();

        // Common stop words to ignore
        Set<String> stopWords = Set.of(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "must", "shall", "can", "need", "dare",
                "to", "of", "in", "for", "on", "with", "at", "by", "from", "as",
                "into", "through", "during", "before", "after", "above", "below",
                "between", "under", "again", "further", "then", "once", "here",
                "there", "when", "where", "why", "how", "all", "each", "few",
                "more", "most", "other", "some", "such", "no", "nor", "not",
                "only", "own", "same", "so", "than", "too", "very", "just",
                "and", "but", "if", "or", "because", "until", "while", "this",
                "that", "these", "those", "it", "its", "they", "them", "their"
        );

        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(word -> word.length() > 2)  // Skip very short words
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void updateInsight(String contextTag, String compressedInsight) {
        repository.findById(contextTag).ifPresent(entity -> {
            entity.setInsightText(compressedInsight);
            repository.save(entity);
            log.info("Compressed memory: tag={}, newLength={}", contextTag, compressedInsight.length());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryEntry> findEntriesNeedingCompression(int thresholdLength) {
        return repository.findEntriesExceedingLength(thresholdLength).stream()
                .map(e -> new MemoryEntry(e.getContextTag(), e.getInsightText()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteEntry(String contextTag) {
        repository.deleteById(contextTag);
        log.info("Deleted memory entry: tag={}", contextTag);
    }
}
