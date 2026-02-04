package com.ai2qa.infra.jpa.repository;

import com.ai2qa.infra.jpa.entity.TestRunEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestRunJpaRepository extends JpaRepository<TestRunEntity, UUID> {
    Optional<TestRunEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<TestRunEntity> findByTenantId(String tenantId);

    Page<TestRunEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<TestRunEntity> findByTenantIdAndStatus(String tenantId, String status);

    Page<TestRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // For finding active runs (status NOT in terminal states)
    List<TestRunEntity> findByStatusNotIn(List<String> statuses);

    @Query("select t.id from TestRunEntity t where t.createdAt < :cutoff")
    List<UUID> findIdsByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
