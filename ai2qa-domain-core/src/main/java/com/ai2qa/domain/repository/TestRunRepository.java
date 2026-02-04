package com.ai2qa.domain.repository;

import com.ai2qa.domain.model.PageRequest;
import com.ai2qa.domain.model.PagedResult;
import com.ai2qa.domain.model.TestRun;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.model.TestRunStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TestRun aggregate.
 *
 * <p>
 * Implementations must:
 * <ul>
 * <li>Persist the aggregate state</li>
 * <li>Poll and persist domain events to outbox in the same transaction</li>
 * <li>Apply tenant isolation</li>
 * </ul>
 */
public interface TestRunRepository {

    /**
     * Saves or updates a test run.
     *
     * @param testRun The test run to save
     * @return The saved test run
     */
    TestRun save(TestRun testRun);

    /**
     * Finds a test run by ID.
     *
     * @param id The test run ID
     * @return Optional containing the test run if found
     */
    Optional<TestRun> findById(TestRunId id);

    /**
     * Finds a test run by ID within a specific tenant.
     *
     * @param id       The test run ID
     * @param tenantId The tenant ID
     * @return Optional containing the test run if found
     */
    Optional<TestRun> findByIdAndTenantId(TestRunId id, String tenantId);

    /**
     * Finds all test runs for a tenant.
     *
     * @param tenantId The tenant ID
     * @return List of test runs
     */
    List<TestRun> findByTenantId(String tenantId);

    /**
     * Finds test runs for a tenant with pagination.
     *
     * @param tenantId    The tenant ID
     * @param pageRequest Pagination parameters
     * @return Paged result of test runs
     */
    PagedResult<TestRun> findByTenantId(String tenantId, PageRequest pageRequest);

    /**
     * Finds all test runs across all tenants, ordered by creation date descending.
     *
     * <p>Intended for admin use only — bypasses tenant isolation.
     *
     * @param pageRequest Pagination parameters
     * @return Paged result of test runs
     */
    PagedResult<TestRun> findAllOrderByCreatedAtDesc(PageRequest pageRequest);

    /**
     * Finds test runs by status for a tenant.
     *
     * @param tenantId The tenant ID
     * @param status   The status to filter by
     * @return List of matching test runs
     */
    List<TestRun> findByTenantIdAndStatus(String tenantId, TestRunStatus status);

    /**
     * Finds all active (non-terminal) test runs.
     *
     * @return List of active test runs
     */
    List<TestRun> findActiveTestRuns();

    /**
     * Finds test run IDs created before the cutoff time.
     *
     * @param cutoff Timestamp threshold
     * @return List of matching test run IDs
     */
    List<TestRunId> findIdsCreatedBefore(java.time.Instant cutoff);

    /**
     * Deletes a test run.
     *
     * @param id The test run ID
     */
    void deleteById(TestRunId id);

    /**
     * Checks if a test run exists.
     *
     * @param id The test run ID
     * @return true if the test run exists
     */
    boolean existsById(TestRunId id);
}
