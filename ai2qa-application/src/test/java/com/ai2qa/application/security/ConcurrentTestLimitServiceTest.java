package com.ai2qa.application.security;

import com.ai2qa.application.exception.ConcurrentLimitExceededException;
import com.ai2qa.application.exception.ConcurrentLimitExceededException.LimitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrentTestLimitServiceTest {

    private ConcurrentTestLimitService service;

    @BeforeEach
    void setUp() {
        // Create service with small limits for testing: 2 per user, 5 global
        service = new ConcurrentTestLimitService(true, 2, 5);
    }

    @Test
    void acquire_shouldSucceedWhenUnderLimits() {
        String tenantId = "tenant-1";
        String testRunId = UUID.randomUUID().toString();

        // Should not throw
        service.acquire(tenantId, testRunId);

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(1);
        assertThat(service.getGlobalActiveCount()).isEqualTo(1);
    }

    @Test
    void acquire_shouldThrowWhenUserLimitExceeded() {
        String tenantId = "tenant-1";

        // Acquire 2 slots (max per user)
        service.acquire(tenantId, UUID.randomUUID().toString());
        service.acquire(tenantId, UUID.randomUUID().toString());

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(2);

        // Third should fail
        assertThatThrownBy(() -> service.acquire(tenantId, UUID.randomUUID().toString()))
                .isInstanceOf(ConcurrentLimitExceededException.class)
                .satisfies(ex -> {
                    ConcurrentLimitExceededException cle = (ConcurrentLimitExceededException) ex;
                    assertThat(cle.getLimitType()).isEqualTo(LimitType.USER);
                    assertThat(cle.getCurrentCount()).isEqualTo(2);
                    assertThat(cle.getMaxAllowed()).isEqualTo(2);
                });
    }

    @Test
    void acquire_shouldThrowWhenGlobalLimitExceeded() {
        // Acquire 5 slots from different tenants (max global)
        for (int i = 0; i < 5; i++) {
            service.acquire("tenant-" + i, UUID.randomUUID().toString());
        }

        assertThat(service.getGlobalActiveCount()).isEqualTo(5);

        // Sixth should fail (global limit)
        assertThatThrownBy(() -> service.acquire("tenant-new", UUID.randomUUID().toString()))
                .isInstanceOf(ConcurrentLimitExceededException.class)
                .satisfies(ex -> {
                    ConcurrentLimitExceededException cle = (ConcurrentLimitExceededException) ex;
                    assertThat(cle.getLimitType()).isEqualTo(LimitType.GLOBAL);
                    assertThat(cle.getCurrentCount()).isEqualTo(5);
                    assertThat(cle.getMaxAllowed()).isEqualTo(5);
                });
    }

    @Test
    void release_shouldDecrementCounts() {
        String tenantId = "tenant-1";
        String testRunId = UUID.randomUUID().toString();

        service.acquire(tenantId, testRunId);
        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(1);
        assertThat(service.getGlobalActiveCount()).isEqualTo(1);

        service.release(tenantId, testRunId);
        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(0);
        assertThat(service.getGlobalActiveCount()).isEqualTo(0);
    }

    @Test
    void release_shouldAllowNewAcquisitionAfterRelease() {
        String tenantId = "tenant-1";

        // Fill up user limit
        String run1 = UUID.randomUUID().toString();
        String run2 = UUID.randomUUID().toString();
        service.acquire(tenantId, run1);
        service.acquire(tenantId, run2);

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(2);

        // Release one
        service.release(tenantId, run1);
        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(1);

        // Now we should be able to acquire again
        String run3 = UUID.randomUUID().toString();
        service.acquire(tenantId, run3);
        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(2);
    }

    @Test
    void release_shouldBeIdempotent() {
        String tenantId = "tenant-1";
        String testRunId = UUID.randomUUID().toString();

        service.acquire(tenantId, testRunId);
        service.release(tenantId, testRunId);
        // Second release should not cause issues
        service.release(tenantId, testRunId);

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(0);
        assertThat(service.getGlobalActiveCount()).isEqualTo(0);
    }

    @Test
    void checkAvailability_shouldReturnEmptyWhenAvailable() {
        String tenantId = "tenant-1";

        Optional<String> result = service.checkAvailability(tenantId);
        assertThat(result).isEmpty();
    }

    @Test
    void checkAvailability_shouldReturnMessageWhenUserLimitReached() {
        String tenantId = "tenant-1";
        service.acquire(tenantId, UUID.randomUUID().toString());
        service.acquire(tenantId, UUID.randomUUID().toString());

        Optional<String> result = service.checkAvailability(tenantId);
        assertThat(result).isPresent();
        assertThat(result.get()).contains("2/2 concurrent tests running");
    }

    @Test
    void checkAvailability_shouldReturnMessageWhenGlobalLimitReached() {
        for (int i = 0; i < 5; i++) {
            service.acquire("tenant-" + i, UUID.randomUUID().toString());
        }

        Optional<String> result = service.checkAvailability("tenant-new");
        assertThat(result).isPresent();
        assertThat(result.get()).contains("5/5 concurrent tests");
    }

    @Test
    void forceReleaseAll_shouldReleaseAllForTenant() {
        String tenantId = "tenant-1";
        service.acquire(tenantId, UUID.randomUUID().toString());
        service.acquire(tenantId, UUID.randomUUID().toString());

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(2);

        service.forceReleaseAll(tenantId);

        assertThat(service.getUserActiveCount(tenantId)).isEqualTo(0);
        assertThat(service.getGlobalActiveCount()).isEqualTo(0);
    }

    @Test
    void getStats_shouldReturnCorrectStatistics() {
        service.acquire("tenant-1", UUID.randomUUID().toString());
        service.acquire("tenant-2", UUID.randomUUID().toString());

        ConcurrentTestLimitService.ConcurrentLimitStats stats = service.getStats();

        assertThat(stats.currentGlobalCount()).isEqualTo(2);
        assertThat(stats.maxGlobal()).isEqualTo(5);
        assertThat(stats.activeUserCount()).isEqualTo(2);
        assertThat(stats.maxPerUser()).isEqualTo(2);
        assertThat(stats.globalUtilization()).isEqualTo(0.4);
    }

    @Test
    void disabled_shouldNotEnforceLimits() {
        // Create disabled service
        ConcurrentTestLimitService disabledService = new ConcurrentTestLimitService(false, 1, 1);

        // Should not throw even when exceeding limits
        disabledService.acquire("tenant-1", UUID.randomUUID().toString());
        disabledService.acquire("tenant-1", UUID.randomUUID().toString());
        disabledService.acquire("tenant-1", UUID.randomUUID().toString());

        // Counts should be 0 because tracking is disabled
        assertThat(disabledService.getUserActiveCount("tenant-1")).isEqualTo(0);
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        /**
         * Tests that concurrent acquire() calls properly enforce limits.
         * Multiple threads race to acquire the last available slot.
         * Only the configured number should succeed; extras should fail.
         */
        @RepeatedTest(10)  // Run multiple times to catch race conditions
        @DisplayName("concurrent acquire should not exceed global limit")
        void concurrentAcquire_shouldNotExceedGlobalLimit() throws InterruptedException {
            // Service with global limit of 5
            ConcurrentTestLimitService testService = new ConcurrentTestLimitService(true, 10, 5);

            int numThreads = 20;  // More threads than slots
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();  // Wait for all threads to be ready
                        testService.acquire("tenant-" + threadId, UUID.randomUUID().toString());
                        successCount.incrementAndGet();
                    } catch (ConcurrentLimitExceededException e) {
                        failureCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();  // Start all threads simultaneously
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly 5 should succeed (global limit)
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failureCount.get()).isEqualTo(15);
            assertThat(testService.getGlobalActiveCount()).isEqualTo(5);
        }

        /**
         * Tests that concurrent acquire() calls for same tenant enforce user limit.
         */
        @RepeatedTest(10)
        @DisplayName("concurrent acquire for same tenant should not exceed user limit")
        void concurrentAcquire_shouldNotExceedUserLimit() throws InterruptedException {
            // Service with user limit of 2, high global limit
            ConcurrentTestLimitService testService = new ConcurrentTestLimitService(true, 2, 100);

            String tenantId = "single-tenant";
            int numThreads = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(numThreads);

            for (int i = 0; i < numThreads; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        testService.acquire(tenantId, UUID.randomUUID().toString());
                        successCount.incrementAndGet();
                    } catch (ConcurrentLimitExceededException e) {
                        failureCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // Exactly 2 should succeed (user limit)
            assertThat(successCount.get()).isEqualTo(2);
            assertThat(failureCount.get()).isEqualTo(8);
            assertThat(testService.getUserActiveCount(tenantId)).isEqualTo(2);
        }

        /**
         * Tests concurrent acquire and release don't corrupt state.
         */
        @RepeatedTest(10)
        @DisplayName("concurrent acquire and release should maintain consistent state")
        void concurrentAcquireAndRelease_shouldMaintainConsistentState() throws InterruptedException {
            ConcurrentTestLimitService testService = new ConcurrentTestLimitService(true, 5, 20);

            String tenantId = "test-tenant";
            int numOperations = 100;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numOperations * 2);
            List<String> acquiredIds = Collections.synchronizedList(new ArrayList<>());

            ExecutorService executor = Executors.newFixedThreadPool(20);

            // Submit acquire operations
            for (int i = 0; i < numOperations; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String testRunId = UUID.randomUUID().toString();
                        try {
                            testService.acquire(tenantId, testRunId);
                            acquiredIds.add(testRunId);
                            // Small delay to simulate work
                            Thread.sleep(1);
                        } catch (ConcurrentLimitExceededException ignored) {
                            // Expected when limit reached
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Submit release operations for previously acquired IDs
            for (int i = 0; i < numOperations; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        Thread.sleep(2);  // Small delay to let some acquires happen first
                        if (!acquiredIds.isEmpty()) {
                            // Try to release a random acquired ID
                            synchronized (acquiredIds) {
                                if (!acquiredIds.isEmpty()) {
                                    String idToRelease = acquiredIds.remove(0);
                                    testService.release(tenantId, idToRelease);
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // State should be consistent: counts should match actual entries
            int userCount = testService.getUserActiveCount(tenantId);
            int globalCount = testService.getGlobalActiveCount();

            // User count and global count should match (same tenant)
            assertThat(userCount).isEqualTo(globalCount);
            // Counts should be non-negative and within limits
            assertThat(userCount).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(5);
            assertThat(globalCount).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(20);
        }

        /**
         * Tests that cleanupStaleEntries doesn't throw ConcurrentModificationException.
         */
        @RepeatedTest(5)
        @DisplayName("cleanup should not throw ConcurrentModificationException")
        void cleanup_shouldNotThrowConcurrentModificationException() throws InterruptedException {
            ConcurrentTestLimitService testService = new ConcurrentTestLimitService(true, 100, 100);

            // Add many entries
            for (int i = 0; i < 50; i++) {
                testService.acquire("tenant-" + i, UUID.randomUUID().toString());
            }

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(3);
            AtomicInteger errors = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(3);

            // Thread 1: Run cleanup repeatedly
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 10; i++) {
                        testService.cleanupStaleEntries();
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 2: Add new entries
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 50; i < 80; i++) {
                        testService.acquire("tenant-" + i, UUID.randomUUID().toString());
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            // Thread 3: Release entries
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < 30; i++) {
                        testService.release("tenant-" + i, UUID.randomUUID().toString());
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // No ConcurrentModificationException or other errors should have occurred
            assertThat(errors.get()).isZero();
        }
    }
}
