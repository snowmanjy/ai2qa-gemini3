package com.ai2qa.application.retention;

import com.ai2qa.application.port.ArtifactStorage;
import com.ai2qa.domain.model.TestRunId;
import com.ai2qa.domain.repository.TestRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RetentionService.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Daily cleanup processes expired runs correctly</li>
 *   <li>Individual failures don't stop the entire job</li>
 *   <li>Batch processing works correctly</li>
 *   <li>Manual deletion works for GDPR requests</li>
 *   <li>DB record deletion is controlled by config flag</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RetentionService Tests")
class RetentionServiceTest {

    @Mock
    private ArtifactStorage artifactStorage;

    @Mock
    private TestRunRepository testRunRepository;

    private Clock fixedClock;
    private RetentionService retentionService;

    private static final Instant NOW = Instant.parse("2026-01-24T00:00:00Z");
    private static final int RETENTION_DAYS = 90;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
    }

    @Nested
    @DisplayName("Daily Cleanup Job")
    class DailyCleanupJob {

        @Test
        @DisplayName("Should cleanup all expired runs successfully")
        void cleanupOldArtifacts_WhenAllSucceed_ReturnsFullSuccess() {
            // Given
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            List<TestRunId> expiredRuns = createTestRunIds(5);
            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(expiredRuns);
            doNothing().when(artifactStorage).deleteArtifacts(any());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.totalFound).isEqualTo(5);
            assertThat(result.successCount).isEqualTo(5);
            assertThat(result.failureCount).isZero();
            assertThat(result.failedRunIds).isEmpty();

            verify(artifactStorage, times(5)).deleteArtifacts(any());
            verify(testRunRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Should continue processing when individual deletions fail")
        void cleanupOldArtifacts_WhenSomeFail_ContinuesAndReportsFailures() {
            // Given
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            List<TestRunId> expiredRuns = createTestRunIds(5);
            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(expiredRuns);

            // First and third succeed, second fails
            doNothing().when(artifactStorage).deleteArtifacts(expiredRuns.get(0).value().toString());
            doThrow(new RuntimeException("GCS error"))
                    .when(artifactStorage).deleteArtifacts(expiredRuns.get(1).value().toString());
            doNothing().when(artifactStorage).deleteArtifacts(expiredRuns.get(2).value().toString());
            doThrow(new RuntimeException("Network error"))
                    .when(artifactStorage).deleteArtifacts(expiredRuns.get(3).value().toString());
            doNothing().when(artifactStorage).deleteArtifacts(expiredRuns.get(4).value().toString());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isFalse();
            assertThat(result.hasFailures()).isTrue();
            assertThat(result.totalFound).isEqualTo(5);
            assertThat(result.successCount).isEqualTo(3);
            assertThat(result.failureCount).isEqualTo(2);
            assertThat(result.failedRunIds).hasSize(2);
            assertThat(result.failedRunIds).contains(
                    expiredRuns.get(1).value().toString(),
                    expiredRuns.get(3).value().toString()
            );

            // All 5 were attempted
            verify(artifactStorage, times(5)).deleteArtifacts(any());
        }

        @Test
        @DisplayName("Should handle empty result set")
        void cleanupOldArtifacts_WhenNoExpiredRuns_ReturnsEmptySuccess() {
            // Given
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(List.of());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.totalFound).isZero();
            assertThat(result.successCount).isZero();
            assertThat(result.failureCount).isZero();

            verify(artifactStorage, never()).deleteArtifacts(any());
        }

        @Test
        @DisplayName("Should process large datasets in batches")
        void cleanupOldArtifacts_WithLargeDataset_ProcessesInBatches() {
            // Given
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            // Create 250 runs (should be split into 3 batches of 100, 100, 50)
            List<TestRunId> expiredRuns = createTestRunIds(250);
            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(expiredRuns);
            doNothing().when(artifactStorage).deleteArtifacts(any());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isTrue();
            assertThat(result.totalFound).isEqualTo(250);
            assertThat(result.successCount).isEqualTo(250);

            verify(artifactStorage, times(250)).deleteArtifacts(any());
        }

        @Test
        @DisplayName("Should handle repository query failure gracefully")
        void cleanupOldArtifacts_WhenQueryFails_ReturnsJobFailure() {
            // Given
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            when(testRunRepository.findIdsCreatedBefore(any()))
                    .thenThrow(new RuntimeException("Database connection failed"));

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.jobFailed).isTrue();
            assertThat(result.jobFailureReason).contains("Database connection failed");
            assertThat(result.successCount).isZero();

            verify(artifactStorage, never()).deleteArtifacts(any());
        }
    }

    @Nested
    @DisplayName("DB Record Deletion")
    class DbRecordDeletion {

        @Test
        @DisplayName("Should delete DB records when enabled")
        void cleanupOldArtifacts_WhenDbDeletionEnabled_DeletesDbRecords() {
            // Given - deleteDbRecords = true
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, true);

            List<TestRunId> expiredRuns = createTestRunIds(3);
            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(expiredRuns);
            doNothing().when(artifactStorage).deleteArtifacts(any());
            doNothing().when(testRunRepository).deleteById(any());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isTrue();
            verify(artifactStorage, times(3)).deleteArtifacts(any());
            verify(testRunRepository, times(3)).deleteById(any());
        }

        @Test
        @DisplayName("Should NOT delete DB records when disabled")
        void cleanupOldArtifacts_WhenDbDeletionDisabled_KeepsDbRecords() {
            // Given - deleteDbRecords = false
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            List<TestRunId> expiredRuns = createTestRunIds(3);
            when(testRunRepository.findIdsCreatedBefore(any())).thenReturn(expiredRuns);
            doNothing().when(artifactStorage).deleteArtifacts(any());

            // When
            RetentionService.CleanupResult result = retentionService.cleanupOldArtifacts();

            // Then
            assertThat(result.isFullySuccessful()).isTrue();
            verify(artifactStorage, times(3)).deleteArtifacts(any());
            verify(testRunRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("Manual Deletion (GDPR)")
    class ManualDeletion {

        @BeforeEach
        void setUp() {
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);
        }

        @Test
        @DisplayName("Should delete artifacts only by default")
        void deleteTestRunArtifacts_WhenCalledWithIdOnly_DeletesArtifactsOnly() {
            // Given
            String testRunId = UUID.randomUUID().toString();
            doNothing().when(artifactStorage).deleteArtifacts(testRunId);

            // When
            boolean result = retentionService.deleteTestRunArtifacts(testRunId);

            // Then
            assertThat(result).isTrue();
            verify(artifactStorage).deleteArtifacts(testRunId);
            verify(testRunRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("Should delete artifacts and DB record when requested")
        void deleteTestRunArtifacts_WhenIncludeDbRecord_DeletesBoth() {
            // Given
            String testRunId = UUID.randomUUID().toString();
            doNothing().when(artifactStorage).deleteArtifacts(testRunId);
            doNothing().when(testRunRepository).deleteById(any());

            // When
            boolean result = retentionService.deleteTestRunArtifacts(testRunId, true);

            // Then
            assertThat(result).isTrue();
            verify(artifactStorage).deleteArtifacts(testRunId);
            verify(testRunRepository).deleteById(any());
        }

        @Test
        @DisplayName("Should return false when deletion fails")
        void deleteTestRunArtifacts_WhenFails_ReturnsFalse() {
            // Given
            String testRunId = UUID.randomUUID().toString();
            doThrow(new RuntimeException("Storage error"))
                    .when(artifactStorage).deleteArtifacts(testRunId);

            // When
            boolean result = retentionService.deleteTestRunArtifacts(testRunId);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("Should use configured retention days")
        void constructor_WithCustomRetentionDays_UsesConfiguredValue() {
            // Given & When
            retentionService = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, 30, false);

            // Then
            assertThat(retentionService.getRetentionDays()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should respect deleteDbRecords flag")
        void constructor_WithDeleteDbRecordsFlag_SetsCorrectly() {
            // Given & When
            RetentionService withDbDelete = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, true);
            RetentionService withoutDbDelete = new RetentionService(
                    artifactStorage, testRunRepository, fixedClock, RETENTION_DAYS, false);

            // Then
            assertThat(withDbDelete.isDeleteDbRecordsEnabled()).isTrue();
            assertThat(withoutDbDelete.isDeleteDbRecordsEnabled()).isFalse();
        }
    }

    // ============== Helper Methods ==============

    private List<TestRunId> createTestRunIds(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new TestRunId(UUID.randomUUID()))
                .toList();
    }
}
