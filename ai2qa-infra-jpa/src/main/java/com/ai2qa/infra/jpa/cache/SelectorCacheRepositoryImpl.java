package com.ai2qa.infra.jpa.cache;

import com.ai2qa.domain.model.CachedSelector;
import com.ai2qa.domain.port.SelectorCachePort;
import com.ai2qa.infra.jpa.encryption.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * JPA implementation of SelectorCachePort.
 */
@Repository
@Transactional
public class SelectorCacheRepositoryImpl implements SelectorCachePort {

    private static final Logger log = LoggerFactory.getLogger(SelectorCacheRepositoryImpl.class);

    private final SelectorCacheJpaRepository jpaRepository;
    private final EncryptionService encryptionService;

    public SelectorCacheRepositoryImpl(
            SelectorCacheJpaRepository jpaRepository,
            EncryptionService encryptionService) {
        this.jpaRepository = jpaRepository;
        this.encryptionService = encryptionService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CachedSelector> find(String tenantId, String goalHash, String urlPattern) {
        return jpaRepository.findByTenantIdAndGoalHashAndUrlPattern(tenantId, goalHash, urlPattern)
                .map(this::toDomain);
    }

    @Override
    public void save(CachedSelector cachedSelector) {
        Optional<SelectorCacheEntity> existing = jpaRepository.findByTenantIdAndGoalHashAndUrlPattern(
                cachedSelector.tenantId(),
                cachedSelector.goalHash(),
                cachedSelector.urlPattern());

        if (existing.isPresent()) {
            // Update existing entry
            SelectorCacheEntity entity = existing.get();
            entity.updateSelector(
                    encryptionService.encrypt(cachedSelector.selector()),
                    cachedSelector.elementDescription(),
                    cachedSelector.lastUsedAt());
            jpaRepository.save(entity);
            log.debug("Updated cached selector for goal hash: {}", cachedSelector.goalHash());
        } else {
            // Create new entry
            SelectorCacheEntity entity = new SelectorCacheEntity(
                    cachedSelector.id(),
                    cachedSelector.tenantId(),
                    cachedSelector.goalHash(),
                    cachedSelector.urlPattern(),
                    encryptionService.encrypt(cachedSelector.selector()),
                    cachedSelector.elementDescription(),
                    cachedSelector.createdAt());
            jpaRepository.save(entity);
            log.debug("Created new cached selector for goal hash: {}", cachedSelector.goalHash());
        }
    }

    @Override
    public void recordSuccess(String tenantId, String goalHash, String urlPattern, Instant now) {
        jpaRepository.findByTenantIdAndGoalHashAndUrlPattern(tenantId, goalHash, urlPattern)
                .ifPresent(entity -> {
                    entity.incrementSuccessCount(now);
                    jpaRepository.save(entity);
                    log.debug("Recorded success for goal hash: {}, count: {}", goalHash, entity.getSuccessCount());
                });
    }

    @Override
    public void recordFailure(String tenantId, String goalHash, String urlPattern, Instant now) {
        jpaRepository.findByTenantIdAndGoalHashAndUrlPattern(tenantId, goalHash, urlPattern)
                .ifPresent(entity -> {
                    entity.incrementFailureCount(now);
                    jpaRepository.save(entity);
                    log.debug("Recorded failure for goal hash: {}, count: {}", goalHash, entity.getFailureCount());

                    // Auto-invalidate if too many failures
                    int total = entity.getSuccessCount() + entity.getFailureCount();
                    if (entity.getFailureCount() >= 3 && total > 0) {
                        int successRate = (entity.getSuccessCount() * 100) / total;
                        if (successRate < 50) {
                            log.warn("Auto-invalidating unreliable selector for goal hash: {}", goalHash);
                            jpaRepository.delete(entity);
                        }
                    }
                });
    }

    @Override
    public void invalidate(String tenantId, String goalHash, String urlPattern) {
        int deleted = jpaRepository.deleteByTenantIdAndGoalHashAndUrlPattern(tenantId, goalHash, urlPattern);
        if (deleted > 0) {
            log.debug("Invalidated cached selector for goal hash: {}", goalHash);
        }
    }

    @Override
    public int cleanupStale(int olderThanDays) {
        Instant cutoff = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);
        int deleted = jpaRepository.deleteStaleEntries(cutoff);
        log.info("Cleaned up {} stale cache entries older than {} days", deleted, olderThanDays);
        return deleted;
    }

    private CachedSelector toDomain(SelectorCacheEntity entity) {
        String decryptedSelector = encryptionService.decrypt(entity.getSelectorValue());
        return new CachedSelector(
                entity.getId(),
                entity.getTenantId(),
                entity.getGoalHash(),
                entity.getUrlPattern(),
                decryptedSelector,
                entity.getElementDescription(),
                entity.getSuccessCount(),
                entity.getFailureCount(),
                entity.getLastUsedAt(),
                entity.getCreatedAt());
    }
}
