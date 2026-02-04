package com.ai2qa.application.retention;

import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.repository.TestRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for data retention policy enforcement.
 * Cleans up old test run artifacts based on configurable retention period.
 *
 * <p>"The Privacy Switch" - ensures we don't keep user data forever.
 *
 * <p>Safety features:
 * <ul>
 *   <li>Batch processing to avoid memory issues with large datasets</li>
 *   <li>Per-item error handling so one failure doesn't stop the entire job</li>
 *   <li>Detailed logging for debugging and auditing</li>
 *   <li>Optional DB record cleanup (disabled by default)</li>
 * </ul>
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    /**
     * Maximum number of runs to process in a single batch.
     * Prevents loading too many IDs into memory at once.
     */
    private static final int BATCH_SIZE = 100;

    private final ArtifactStorage artifactStorage;
    private final TestRunRepository testRunRepository;
    private final Clock clock;
    private final int retentionDays;
    private final boolean deleteDbRecords;

    public RetentionService(
            ArtifactStorage artifactStorage,
            TestRunRepository testRunRepository,
            Clock clock,
            @Value("${ai2qa.retention.days:90}") int retentionDays,
            @Value("${ai2qa.retention.delete-db-records:false}") boolean deleteDbRecords) {
        this.artifactStorage = artifactStorage;
        this.testRunRepository = testRunRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.deleteDbRecords = deleteDbRecords;
        log.info("Retention Service initialized: {} days retention, deleteDbRecords={}", retentionDays, deleteDbRecords);
    }

    /**
     * Runs daily at midnight to clean up old test run artifacts.
     *
     * <p>Cron: 0 0 0 * * * = Every day at 00:00:00
     *
     * @return Cleanup result with success/failure counts
     */
    @Scheduled(cron = "0 0 0 * * *")
    public CleanupResult cleanupOldArtifacts() {
        log.info("[RETENTION] Starting daily artifact cleanup (retention: {} days)", retentionDays);

        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        CleanupResult result = new CleanupResult();

        try {
            // Process in batches to avoid memory issues
            List<TestRunId> expiredRuns = testRunRepository.findIdsCreatedBefore(cutoff);
            result.totalFound = expiredRuns.size();

            log.info("[RETENTION] Found {} expired runs to clean up", expiredRuns.size());

            // Process in batches
            List<List<TestRunId>> batches = partition(expiredRuns, BATCH_SIZE);
            int batchNum = 0;

            for (List<TestRunId> batch : batches) {
                batchNum++;
                log.debug("[RETENTION] Processing batch {}/{} ({} runs)",
                        batchNum, batches.size(), batch.size());

                for (TestRunId runId : batch) {
                    try {
                        // Delete GCS artifacts
                        artifactStorage.deleteArtifacts(runId.value().toString());

                        // Optionally delete DB records
                        if (deleteDbRecords) {
                            testRunRepository.deleteById(runId);
                        }

                        result.successCount++;
                        log.debug("[RETENTION] Cleaned up run: {}", runId.value());

                    } catch (Exception e) {
                        result.failureCount++;
                        result.failedRunIds.add(runId.value().toString());
                        log.warn("[RETENTION] Failed to clean up run {}: {}",
                                runId.value(), e.getMessage());
                    }
                }
            }

            log.info("[RETENTION] Cleanup completed. Success: {}, Failures: {}, Total: {}",
                    result.successCount, result.failureCount, result.totalFound);

        } catch (Exception e) {
            log.error("[RETENTION] Cleanup job failed unexpectedly", e);
            result.jobFailed = true;
            result.jobFailureReason = e.getMessage();
        }

        return result;
    }

    /**
     * Manually triggers cleanup for a specific test run.
     * Can be called from an admin endpoint for GDPR deletion requests.
     *
     * @param testRunId The test run to delete
     * @param includeDbRecord Whether to also delete the database record
     * @return true if successful
     */
    public boolean deleteTestRunArtifacts(String testRunId, boolean includeDbRecord) {
        log.info("[RETENTION] Manual deletion requested for test run: {} (includeDb={})",
                testRunId, includeDbRecord);

        try {
            artifactStorage.deleteArtifacts(testRunId);

            if (includeDbRecord) {
                testRunRepository.deleteById(new TestRunId(java.util.UUID.fromString(testRunId)));
            }

            log.info("[RETENTION] Successfully deleted artifacts for test run: {}", testRunId);
            return true;

        } catch (Exception e) {
            log.error("[RETENTION] Failed to delete artifacts for test run {}: {}",
                    testRunId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Manually triggers cleanup for a specific test run (artifacts only).
     * Backwards-compatible overload.
     */
    public boolean deleteTestRunArtifacts(String testRunId) {
        return deleteTestRunArtifacts(testRunId, false);
    }

    /**
     * Partitions a list into batches of the specified size.
     */
    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }

    /**
     * Result of a cleanup operation.
     */
    public static class CleanupResult {
        public int totalFound = 0;
        public int successCount = 0;
        public int failureCount = 0;
        public List<String> failedRunIds = new ArrayList<>();
        public boolean jobFailed = false;
        public String jobFailureReason = null;

        public boolean isFullySuccessful() {
            return !jobFailed && failureCount == 0;
        }

        public boolean hasFailures() {
            return failureCount > 0 || jobFailed;
        }
    }

    // ============== Getters for testing ==============

    int getRetentionDays() {
        return retentionDays;
    }

    boolean isDeleteDbRecordsEnabled() {
        return deleteDbRecords;
    }
}
