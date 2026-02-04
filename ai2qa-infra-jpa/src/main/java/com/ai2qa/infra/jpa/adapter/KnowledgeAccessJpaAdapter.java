package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.port.KnowledgeAccessPort;
import com.ai2qa.infra.jpa.entity.KnowledgeAccessLogEntity;
import com.ai2qa.infra.jpa.repository.KnowledgeAccessLogJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA adapter implementing the KnowledgeAccessPort.
 *
 * <p>Provides knowledge access logging and rate limiting queries
 * for the anti-scraping guard.
 */
@Component
public class KnowledgeAccessJpaAdapter implements KnowledgeAccessPort {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAccessJpaAdapter.class);

    private final KnowledgeAccessLogJpaRepository repository;

    public KnowledgeAccessJpaAdapter(KnowledgeAccessLogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countRecentAccess(String tenantId, Instant since) {
        return repository.countRecentAccess(tenantId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUniqueDomainsAccessed(String tenantId, Instant since) {
        return repository.countUniqueDomainsAccessed(tenantId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPatternsAccessed(String tenantId) {
        return repository.countPatternsAccessed(tenantId);
    }

    @Override
    @Transactional
    public void logAccess(String tenantId, String domain, int patternsAccessed, BigDecimal creditsCharged) {
        KnowledgeAccessLogEntity entity = new KnowledgeAccessLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setAccessType(KnowledgeAccessLogEntity.AccessTypeEnum.RENT);
        entity.setDomain(domain);
        entity.setPatternsAccessed(patternsAccessed);
        entity.setPatternsContributed(0);
        entity.setCreditsCharged(creditsCharged);
        entity.setAccessedAt(Instant.now());

        repository.save(entity);
        log.debug("[KNOWLEDGE_ACCESS] Logged RENT: tenant={}, domain={}, patterns={}, credits={}",
                tenantId, domain, patternsAccessed, creditsCharged);
    }

    @Override
    @Transactional
    public void logContribution(String tenantId, String domain, int patternsContributed) {
        KnowledgeAccessLogEntity entity = new KnowledgeAccessLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setAccessType(KnowledgeAccessLogEntity.AccessTypeEnum.LEARN);
        entity.setDomain(domain);
        entity.setPatternsAccessed(0);
        entity.setPatternsContributed(patternsContributed);
        entity.setCreditsCharged(BigDecimal.ZERO);
        entity.setAccessedAt(Instant.now());

        repository.save(entity);
        log.debug("[KNOWLEDGE_ACCESS] Logged LEARN: tenant={}, domain={}, patterns={}",
                tenantId, domain, patternsContributed);
    }
}
