package com.ai2qa.infra.jpa.adapter;

import com.ai2qa.domain.context.AdminContext;
import com.ai2qa.domain.context.TenantContext;
import com.ai2qa.domain.factory.ActionStepFactory;
import com.ai2qa.domain.model.ActionStep;
import com.ai2qa.domain.model.ExecutedStep;
import com.ai2qa.domain.factory.PagedResultFactory;
import com.ai2qa.domain.model.PageRequest;
import com.ai2qa.domain.model.PagedResult;
import com.ai2qa.domain.model.RunSummary;
import com.ai2qa.domain.model.TestPersona;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.TestRunStatus;
import com.ai2qa.domain.model.ExecutionMode;
import com.ai2qa.domain.model.SummaryStatus;
import com.ai2qa.domain.repository.TestRunRepository;
import com.ai2qa.infra.jpa.entity.TestRunEntity;
import com.ai2qa.infra.jpa.repository.TestRunJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

@Repository
public class TestRunRepositoryImpl implements TestRunRepository {

    private final TestRunJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    public TestRunRepositoryImpl(TestRunJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public TestRun save(TestRun testRun) {
        // Enforce tenant isolation on save if context is set
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !contextTenant.equals(testRun.getTenantId())) {
            throw new SecurityException("Cannot save TestRun for different tenant");
        }

        try {
            // Check if entity already exists to avoid Hibernate NonUniqueObjectException
            Optional<TestRunEntity> existing = jpaRepository.findById(testRun.getId().value());

            TestRunEntity saved;
            if (existing.isPresent()) {
                // Update existing entity in place to avoid session conflicts
                TestRunEntity entity = existing.get();
                entity.setStatus(testRun.getStatus().name());
                entity.setPlanJson(objectMapper.writeValueAsString(
                        testRun.getPlannedSteps().stream().map(this::toPayload).toList()));
                entity.setExecutedStepsJson(objectMapper.writeValueAsString(testRun.getExecutedSteps()));
                entity.setStartedAt(testRun.getStartedAt().orElse(null));
                entity.setCompletedAt(testRun.getCompletedAt().orElse(null));
                entity.setFailureReason(testRun.getFailureReason().orElse(null));
                entity.setSummaryJson(testRun.getSummary()
                        .map(summary -> {
                            try {
                                return objectMapper.writeValueAsString(summary);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException("Failed to serialize summary", e);
                            }
                        })
                        .orElse(null));
                entity.setSummaryStatus(testRun.getSummaryStatus().name());
                saved = jpaRepository.save(entity);
            } else {
                // New entity - create fresh
                TestRunEntity entity = toEntity(testRun);
                saved = jpaRepository.save(entity);
            }
            return toDomain(saved);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize TestRun data", e);
        }
    }

    @Override
    public Optional<TestRun> findById(TestRunId id) {
        // Admin users bypass tenant isolation to access any run
        if (AdminContext.isAdmin()) {
            return jpaRepository.findById(id.value()).map(this::toDomain);
        }
        // Enforce tenant isolation on read
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return findByIdAndTenantId(id, tenantId);
        }
        // Fallback for internal usage (e.g. background workers) without tenant context
        return jpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    public Optional<TestRun> findByIdAndTenantId(TestRunId id, String tenantId) {
        return jpaRepository.findByIdAndTenantId(id.value(), tenantId)
                .map(this::toDomain);
    }

    @Override
    public List<TestRun> findByTenantId(String tenantId) {
        // Enforce matching context if present
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            return Collections.emptyList();
        }
        return jpaRepository.findByTenantId(tenantId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PagedResult<TestRun> findByTenantId(String tenantId, PageRequest pageRequest) {
        // Enforce matching context if present
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            return PagedResultFactory.empty(pageRequest);
        }

        // Convert domain PageRequest to Spring Pageable
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(),
                pageRequest.size(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        Page<TestRunEntity> page = jpaRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);

        // Convert Spring Page to domain PagedResult
        List<TestRun> content = page.getContent().stream()
                .map(this::toDomain)
                .toList();

        return PagedResultFactory.of(content, pageRequest, page.getTotalElements());
    }

    @Override
    public PagedResult<TestRun> findAllOrderByCreatedAtDesc(PageRequest pageRequest) {
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.page(),
                pageRequest.size(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        Page<TestRunEntity> page = jpaRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<TestRun> content = page.getContent().stream()
                .map(this::toDomain)
                .toList();

        return PagedResultFactory.of(content, pageRequest, page.getTotalElements());
    }

    @Override
    public List<TestRun> findByTenantIdAndStatus(String tenantId, TestRunStatus status) {
        // Enforce matching context if present
        String contextTenant = TenantContext.getTenantId();
        if (contextTenant != null && !contextTenant.equals(tenantId)) {
            return Collections.emptyList();
        }
        return jpaRepository.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TestRun> findActiveTestRuns() {
        // Active runs are those NOT COMPLETED, FAILED, or CANCELLED
        List<String> terminalStatuses = List.of(
                TestRunStatus.COMPLETED.name(),
                TestRunStatus.FAILED.name(),
                TestRunStatus.CANCELLED.name());
        return jpaRepository.findByStatusNotIn(terminalStatuses).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TestRunId> findIdsCreatedBefore(Instant cutoff) {
        return jpaRepository.findIdsByCreatedAtBefore(cutoff).stream()
                .map(TestRunId::new)
                .toList();
    }

    @Override
    public void deleteById(TestRunId id) {
        // Enforce tenant isolation
        findById(id).ifPresent(run -> jpaRepository.deleteById(run.getId().value()));
    }

    @Override
    public boolean existsById(TestRunId id) {
        if (AdminContext.isAdmin()) {
            return jpaRepository.existsById(id.value());
        }
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return jpaRepository.findByIdAndTenantId(id.value(), tenantId).isPresent();
        }
        return jpaRepository.existsById(id.value());
    }

    private TestRunEntity toEntity(TestRun domain) throws JsonProcessingException {
        List<ActionStepPayload> planPayloads = domain.getPlannedSteps().stream()
                .map(this::toPayload)
                .toList();
        String summaryJson = domain.getSummary()
                .map(summary -> {
                    try {
                        return objectMapper.writeValueAsString(summary);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to serialize summary", e);
                    }
                })
                .orElse(null);
        return new TestRunEntity(
                domain.getId().value(),
                domain.getTenantId(),
                domain.getTargetUrl(),
                domain.getStatus().name(),
                domain.getPersona().name(),
                objectMapper.writeValueAsString(domain.getGoals()),
                objectMapper.writeValueAsString(planPayloads),
                objectMapper.writeValueAsString(domain.getExecutedSteps()),
                domain.getStartedAt().orElse(null),
                domain.getCompletedAt().orElse(null),
                domain.getFailureReason().orElse(null),
                summaryJson,
                domain.getSummaryStatus().name(),
                domain.isNotifyOnComplete(),
                domain.getNotificationEmail().orElse(null),
                domain.getExecutionMode().name(),
                null,  // Local agent removed
                domain.getCreatedAt());
    }

    private TestRun toDomain(TestRunEntity entity) {
        try {
            List<String> goals = objectMapper.readValue(entity.getGoalsJson(), new TypeReference<>() {
            });
            List<ActionStepPayload> planPayloads = objectMapper.readValue(entity.getPlanJson(), new TypeReference<>() {
            });
            List<ActionStep> plan = planPayloads.stream()
                    .map(this::toActionStep)
                    .toList();
            List<ExecutedStep> executed = objectMapper.readValue(entity.getExecutedStepsJson(), new TypeReference<>() {
            });

            // Parse persona, default to STANDARD if null or invalid
            TestPersona persona = TestPersona.STANDARD;
            if (entity.getPersona() != null) {
                try {
                    persona = TestPersona.valueOf(entity.getPersona());
                } catch (IllegalArgumentException e) {
                    // Default to STANDARD for unknown persona values
                }
            }

            // Parse summary if present
            RunSummary summary = null;
            if (entity.getSummaryJson() != null && !entity.getSummaryJson().isBlank()) {
                summary = objectMapper.readValue(entity.getSummaryJson(), RunSummary.class);
            }

            // Parse summary status, default to PENDING if null or invalid
            SummaryStatus summaryStatus = SummaryStatus.PENDING;
            if (entity.getSummaryStatus() != null) {
                try {
                    summaryStatus = SummaryStatus.valueOf(entity.getSummaryStatus());
                } catch (IllegalArgumentException e) {
                    // Default to PENDING for unknown status values
                }
            }

            // Parse execution mode, default to CLOUD if null or invalid
            ExecutionMode executionMode = ExecutionMode.CLOUD;
            if (entity.getExecutionMode() != null) {
                try {
                    executionMode = ExecutionMode.valueOf(entity.getExecutionMode());
                } catch (IllegalArgumentException e) {
                    // Default to CLOUD for unknown execution mode values
                }
            }

            // Local agent removed, agentId always null

            return TestRun.reconstitute(
                    new TestRunId(entity.getId()),
                    entity.getTenantId(),
                    entity.getTargetUrl(),
                    goals,
                    persona,
                    null,  // cookiesJson is transient, never persisted
                    entity.isNotifyOnComplete(),
                    entity.getNotificationEmail(),
                    TestRunStatus.valueOf(entity.getStatus()),
                    entity.getCreatedAt(),
                    entity.getStartedAt(),
                    entity.getCompletedAt(),
                    plan,
                    executed,
                    entity.getFailureReason(),
                    summary,
                    summaryStatus,
                    executionMode);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize TestRun data", e);
        }
    }

    private ActionStepPayload toPayload(ActionStep step) {
        Map<String, Object> params = new HashMap<>();
        step.params().forEach(params::put);
        return new ActionStepPayload(
                step.stepId(),
                step.action(),
                step.target(),
                step.selector().orElse(null),
                step.value().orElse(null),
                params
        );
    }

    private ActionStep toActionStep(ActionStepPayload payload) {
        return ActionStepFactory.reconstitute(
                payload.stepId(),
                payload.action(),
                payload.target(),
                payload.selector(),
                payload.value(),
                toStringMap(payload.params())
        );
    }

    private Map<String, String> toStringMap(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Map<String, String> converted = new HashMap<>();
        params.forEach((key, value) -> converted.put(key, value == null ? "" : value.toString()));
        return Map.copyOf(converted);
    }

    private record ActionStepPayload(
            String stepId,
            String action,
            String target,
            String selector,
            String value,
            Map<String, Object> params
    ) { }
}
