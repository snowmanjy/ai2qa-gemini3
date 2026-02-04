package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.model.knowledge.FlowStep;
import com.ai2qa.domain.model.knowledge.FlowStrategy;
import com.ai2qa.domain.model.knowledge.FlowStrategyFactory;
import com.ai2qa.domain.model.knowledge.FlowStrategyId;
import com.ai2qa.domain.model.knowledge.PatternType;
import com.ai2qa.domain.model.knowledge.SelectorAlternative;
import com.ai2qa.domain.model.knowledge.SelectorType;
import com.ai2qa.domain.model.knowledge.SitePattern;
import com.ai2qa.domain.model.knowledge.SitePatternFactory;
import com.ai2qa.domain.model.knowledge.SitePatternId;
import com.ai2qa.domain.model.knowledge.Visibility;
import com.ai2qa.domain.repository.KnowledgeRepository;
import com.ai2qa.infra.jpa.entity.FlowStrategyEntity;
import com.ai2qa.infra.jpa.entity.KnowledgeAccessLogEntity;
import com.ai2qa.infra.jpa.entity.SelectorAlternativeEntity;
import com.ai2qa.infra.jpa.entity.SitePatternEntity;
import com.ai2qa.infra.jpa.repository.FlowStrategyJpaRepository;
import com.ai2qa.infra.jpa.repository.KnowledgeAccessLogJpaRepository;
import com.ai2qa.infra.jpa.repository.SelectorAlternativeJpaRepository;
import com.ai2qa.infra.jpa.repository.SitePatternJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of KnowledgeRepository.
 */
@Repository
@Transactional
public class KnowledgeRepositoryImpl implements KnowledgeRepository {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRepositoryImpl.class);

    private final SitePatternJpaRepository sitePatternRepo;
    private final SelectorAlternativeJpaRepository selectorAltRepo;
    private final FlowStrategyJpaRepository flowStrategyRepo;
    private final KnowledgeAccessLogJpaRepository accessLogRepo;
    private final ObjectMapper objectMapper;

    public KnowledgeRepositoryImpl(
            SitePatternJpaRepository sitePatternRepo,
            SelectorAlternativeJpaRepository selectorAltRepo,
            FlowStrategyJpaRepository flowStrategyRepo,
            KnowledgeAccessLogJpaRepository accessLogRepo,
            ObjectMapper objectMapper) {
        this.sitePatternRepo = sitePatternRepo;
        this.selectorAltRepo = selectorAltRepo;
        this.flowStrategyRepo = flowStrategyRepo;
        this.accessLogRepo = accessLogRepo;
        this.objectMapper = objectMapper;
    }

    // ============== Site Patterns ==============

    @Override
    public SitePattern savePattern(SitePattern pattern) {
        // Check if entity already exists to avoid Hibernate NonUniqueObjectException
        Optional<SitePatternEntity> existing = sitePatternRepo.findById(pattern.id().value());

        SitePatternEntity saved;
        if (existing.isPresent()) {
            // Update existing entity in place to avoid session conflicts
            SitePatternEntity entity = existing.get();
            entity.setPatternValue(pattern.value());
            entity.setConfidenceScore(pattern.confidenceScore());
            entity.setSuccessCount(pattern.successCount());
            entity.setFailureCount(pattern.failureCount());
            pattern.avgDurationMs().ifPresent(entity::setAvgDurationMs);
            pattern.lastSeenAt().ifPresent(entity::setLastSeenAt);
            entity.setVisibility(toEntityVisibility(pattern.visibility()));
            entity.setVersion(pattern.version());
            saved = sitePatternRepo.save(entity);
        } else {
            // New entity - create fresh
            SitePatternEntity entity = toEntity(pattern);
            saved = sitePatternRepo.save(entity);
        }
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SitePattern> findPatternById(SitePatternId id) {
        return sitePatternRepo.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SitePattern> findPattern(String domain, PatternType type, String key, String tenantId) {
        return sitePatternRepo.findByDomainAndTypeAndKeyAndTenant(
                domain.toLowerCase(),
                toEntityType(type),
                key.toLowerCase(),
                tenantId
        ).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SitePattern> findPatternsByDomain(String domain) {
        return sitePatternRepo.findByDomainOrderByConfidenceScoreDesc(domain.toLowerCase())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SitePattern> findPatternsWithAccess(String domain, String tenantId) {
        return sitePatternRepo.findPatternsWithAccess(domain.toLowerCase(), tenantId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SitePattern> findTopPatterns(String domain, int limit) {
        return sitePatternRepo.findTopPatterns(domain.toLowerCase(), limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updatePatternStats(SitePatternId id, boolean success, Integer durationMs) {
        if (success) {
            sitePatternRepo.updateSuccessStats(id.value(), durationMs);
        } else {
            sitePatternRepo.updateFailureStats(id.value());
        }
    }

    // ============== Selector Alternatives ==============

    @Override
    public SelectorAlternative saveAlternative(SelectorAlternative alternative) {
        // Check if entity already exists to avoid Hibernate NonUniqueObjectException
        Optional<SelectorAlternativeEntity> existing = selectorAltRepo.findById(alternative.id());

        SelectorAlternativeEntity saved;
        if (existing.isPresent()) {
            // Update existing entity in place to avoid session conflicts
            SelectorAlternativeEntity entity = existing.get();
            entity.setSelectorType(toEntitySelectorType(alternative.type()));
            entity.setSelectorValue(alternative.value());
            entity.setPriority(alternative.priority());
            entity.setSuccessRate(alternative.successRate());
            entity.setSuccessCount(alternative.successCount());
            entity.setFailureCount(alternative.failureCount());
            saved = selectorAltRepo.save(entity);
        } else {
            // New entity - create fresh
            SelectorAlternativeEntity entity = toEntity(alternative);
            saved = selectorAltRepo.save(entity);
        }
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SelectorAlternative> findAlternatives(SitePatternId sitePatternId) {
        return selectorAltRepo.findBySitePatternIdOrderByPriorityDesc(sitePatternId.value())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateAlternativeStats(UUID alternativeId, boolean success) {
        if (success) {
            selectorAltRepo.updateSuccessStats(alternativeId);
        } else {
            selectorAltRepo.updateFailureStats(alternativeId);
        }
    }

    // ============== Flow Strategies ==============

    @Override
    public FlowStrategy saveStrategy(FlowStrategy strategy) {
        // Check if entity already exists to avoid Hibernate NonUniqueObjectException
        Optional<FlowStrategyEntity> existing = flowStrategyRepo.findById(strategy.id().value());

        FlowStrategyEntity saved;
        if (existing.isPresent()) {
            // Update existing entity in place to avoid session conflicts
            FlowStrategyEntity entity = existing.get();
            strategy.domain().ifPresent(entity::setDomain);
            entity.setFlowName(strategy.flowName());
            strategy.description().ifPresent(entity::setDescription);
            entity.setStepsJson(serializeSteps(strategy.steps()));
            entity.setSuccessCount(strategy.successCount());
            entity.setFailureCount(strategy.failureCount());
            strategy.avgDurationMs().ifPresent(entity::setAvgDurationMs);
            entity.setVisibility(toEntityVisibility(strategy.visibility()));
            entity.setUpdatedAt(strategy.updatedAt());
            entity.setVersion(strategy.version());
            saved = flowStrategyRepo.save(entity);
        } else {
            // New entity - create fresh
            FlowStrategyEntity entity = toEntity(strategy);
            saved = flowStrategyRepo.save(entity);
        }
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FlowStrategy> findStrategyById(FlowStrategyId id) {
        return flowStrategyRepo.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowStrategy> findStrategies(String domain, String flowName) {
        return flowStrategyRepo.findByDomainAndFlowName(domain.toLowerCase(), flowName.toLowerCase())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowStrategy> findStrategiesWithAccess(String domain, String flowName, String tenantId) {
        return flowStrategyRepo.findStrategiesWithAccess(
                domain.toLowerCase(), flowName.toLowerCase(), tenantId)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void updateStrategyStats(FlowStrategyId id, boolean success, Integer durationMs) {
        if (success) {
            flowStrategyRepo.updateSuccessStats(id.value(), durationMs);
        } else {
            flowStrategyRepo.updateFailureStats(id.value());
        }
    }

    // ============== Statistics ==============

    @Override
    @Transactional(readOnly = true)
    public long countPatternsContributed(String tenantId) {
        return sitePatternRepo.countByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPatternsAccessed(String tenantId) {
        return accessLogRepo.countPatternsAccessed(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countDomainsCovered() {
        return sitePatternRepo.countUniqueDomains();
    }

    // ============== Mapping Methods ==============

    private SitePatternEntity toEntity(SitePattern pattern) {
        SitePatternEntity entity = new SitePatternEntity();
        entity.setId(pattern.id().value());
        entity.setDomain(pattern.domain());
        entity.setPatternType(toEntityType(pattern.type()));
        entity.setPatternKey(pattern.key());
        entity.setPatternValue(pattern.value());
        entity.setConfidenceScore(pattern.confidenceScore());
        entity.setSuccessCount(pattern.successCount());
        entity.setFailureCount(pattern.failureCount());
        pattern.avgDurationMs().ifPresent(entity::setAvgDurationMs);
        pattern.lastSeenAt().ifPresent(entity::setLastSeenAt);
        entity.setCreatedAt(pattern.createdAt());
        pattern.createdByRunId().ifPresent(entity::setCreatedByRunId);
        entity.setVisibility(toEntityVisibility(pattern.visibility()));
        pattern.tenantId().ifPresent(entity::setTenantId);
        entity.setVersion(pattern.version());
        return entity;
    }

    private SitePattern toDomain(SitePatternEntity entity) {
        return SitePatternFactory.reconstitute(
                entity.getId(),
                entity.getDomain(),
                toDomainType(entity.getPatternType()),
                entity.getPatternKey(),
                entity.getPatternValue(),
                entity.getConfidenceScore(),
                entity.getSuccessCount(),
                entity.getFailureCount(),
                entity.getAvgDurationMs(),
                entity.getLastSeenAt(),
                entity.getCreatedAt(),
                entity.getCreatedByRunId(),
                toDomainVisibility(entity.getVisibility()),
                entity.getTenantId(),
                entity.getVersion()
        );
    }

    private SelectorAlternativeEntity toEntity(SelectorAlternative alt) {
        SelectorAlternativeEntity entity = new SelectorAlternativeEntity();
        entity.setId(alt.id());
        entity.setSitePatternId(alt.sitePatternId().value());
        entity.setSelectorType(toEntitySelectorType(alt.type()));
        entity.setSelectorValue(alt.value());
        entity.setPriority(alt.priority());
        entity.setSuccessRate(alt.successRate());
        entity.setSuccessCount(alt.successCount());
        entity.setFailureCount(alt.failureCount());
        entity.setCreatedAt(alt.createdAt());
        return entity;
    }

    private SelectorAlternative toDomain(SelectorAlternativeEntity entity) {
        return new SelectorAlternative(
                entity.getId(),
                SitePatternId.reconstitute(entity.getSitePatternId()),
                toDomainSelectorType(entity.getSelectorType()),
                entity.getSelectorValue(),
                entity.getPriority(),
                entity.getSuccessRate(),
                entity.getSuccessCount(),
                entity.getFailureCount(),
                entity.getCreatedAt()
        );
    }

    private FlowStrategyEntity toEntity(FlowStrategy strategy) {
        FlowStrategyEntity entity = new FlowStrategyEntity();
        entity.setId(strategy.id().value());
        strategy.domain().ifPresent(entity::setDomain);
        entity.setFlowName(strategy.flowName());
        strategy.description().ifPresent(entity::setDescription);
        entity.setStepsJson(serializeSteps(strategy.steps()));
        entity.setSuccessCount(strategy.successCount());
        entity.setFailureCount(strategy.failureCount());
        strategy.avgDurationMs().ifPresent(entity::setAvgDurationMs);
        entity.setVisibility(toEntityVisibility(strategy.visibility()));
        strategy.tenantId().ifPresent(entity::setTenantId);
        entity.setCreatedAt(strategy.createdAt());
        entity.setUpdatedAt(strategy.updatedAt());
        entity.setVersion(strategy.version());
        return entity;
    }

    private FlowStrategy toDomain(FlowStrategyEntity entity) {
        return FlowStrategyFactory.reconstitute(
                entity.getId(),
                entity.getDomain(),
                entity.getFlowName(),
                entity.getDescription(),
                deserializeSteps(entity.getStepsJson()),
                entity.getSuccessCount(),
                entity.getFailureCount(),
                entity.getAvgDurationMs(),
                toDomainVisibility(entity.getVisibility()),
                entity.getTenantId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    // ============== Enum Mappings ==============

    private SitePatternEntity.PatternTypeEnum toEntityType(PatternType type) {
        return switch (type) {
            case SELECTOR -> SitePatternEntity.PatternTypeEnum.SELECTOR;
            case TIMING -> SitePatternEntity.PatternTypeEnum.TIMING;
            case AUTH -> SitePatternEntity.PatternTypeEnum.AUTH;
            case QUIRK -> SitePatternEntity.PatternTypeEnum.QUIRK;
        };
    }

    private PatternType toDomainType(SitePatternEntity.PatternTypeEnum type) {
        return switch (type) {
            case SELECTOR -> PatternType.SELECTOR;
            case TIMING -> PatternType.TIMING;
            case AUTH -> PatternType.AUTH;
            case QUIRK -> PatternType.QUIRK;
        };
    }

    private SitePatternEntity.VisibilityEnum toEntityVisibility(Visibility visibility) {
        return switch (visibility) {
            case GLOBAL -> SitePatternEntity.VisibilityEnum.GLOBAL;
            case TENANT -> SitePatternEntity.VisibilityEnum.TENANT;
            case PRIVATE -> SitePatternEntity.VisibilityEnum.PRIVATE;
        };
    }

    private Visibility toDomainVisibility(SitePatternEntity.VisibilityEnum visibility) {
        return switch (visibility) {
            case GLOBAL -> Visibility.GLOBAL;
            case TENANT -> Visibility.TENANT;
            case PRIVATE -> Visibility.PRIVATE;
        };
    }

    private SelectorAlternativeEntity.SelectorTypeEnum toEntitySelectorType(SelectorType type) {
        return switch (type) {
            case CSS -> SelectorAlternativeEntity.SelectorTypeEnum.CSS;
            case XPATH -> SelectorAlternativeEntity.SelectorTypeEnum.XPATH;
            case TEXT -> SelectorAlternativeEntity.SelectorTypeEnum.TEXT;
            case ARIA -> SelectorAlternativeEntity.SelectorTypeEnum.ARIA;
            case DATA_TESTID -> SelectorAlternativeEntity.SelectorTypeEnum.DATA_TESTID;
        };
    }

    private SelectorType toDomainSelectorType(SelectorAlternativeEntity.SelectorTypeEnum type) {
        return switch (type) {
            case CSS -> SelectorType.CSS;
            case XPATH -> SelectorType.XPATH;
            case TEXT -> SelectorType.TEXT;
            case ARIA -> SelectorType.ARIA;
            case DATA_TESTID -> SelectorType.DATA_TESTID;
        };
    }

    // ============== JSON Serialization ==============

    private String serializeSteps(List<FlowStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flow steps", e);
            return "[]";
        }
    }

    private List<FlowStep> deserializeSteps(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<FlowStep>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize flow steps", e);
            return List.of();
        }
    }
}
