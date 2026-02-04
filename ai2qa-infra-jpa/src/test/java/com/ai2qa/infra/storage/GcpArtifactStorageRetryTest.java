package com.ai2qa.infra.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for GcpArtifactStorage retry logic.
 * Verifies that transient network failures are retried appropriately.
 */
@ExtendWith(MockitoExtension.class)
class GcpArtifactStorageRetryTest {

    @Mock
    private Storage mockStorage;

    private GcpArtifactStorage artifactStorage;

    @BeforeEach
    void setUp() throws Exception {
        // Create instance and inject mock storage via reflection
        artifactStorage = new GcpArtifactStorage("test-bucket");

        Field storageField = GcpArtifactStorage.class.getDeclaredField("storage");
        storageField.setAccessible(true);
        storageField.set(artifactStorage, mockStorage);
    }

    @Nested
    @DisplayName("Screenshot Upload Retry")
    class ScreenshotUploadRetry {

        @Test
        @DisplayName("succeeds on first attempt - no retry needed")
        void succeedsOnFirstAttempt() {
            // Given
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenReturn(null); // Success

            // When
            artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3});

            // Then
            verify(mockStorage, times(1)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("retries on 'Error writing request body' and succeeds")
        void retriesOnConnectionErrorAndSucceeds() {
            // Given - fails first, succeeds second
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Error writing request body to server"))
                    .thenReturn(null); // Success on retry

            // When
            artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3});

            // Then - should have retried
            verify(mockStorage, times(2)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("retries on 'Broken pipe' and succeeds")
        void retriesOnBrokenPipeAndSucceeds() {
            // Given - fails first, succeeds second
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Broken pipe"))
                    .thenReturn(null);

            // When
            artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3});

            // Then
            verify(mockStorage, times(2)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("retries on 'Connection reset' and succeeds")
        void retriesOnConnectionResetAndSucceeds() {
            // Given
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Connection reset by peer"))
                    .thenReturn(null);

            // When
            artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3});

            // Then
            verify(mockStorage, times(2)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("throws after max retries exhausted")
        void throwsAfterMaxRetriesExhausted() {
            // Given - always fails
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Error writing request body to server"));

            // When/Then
            assertThatThrownBy(() ->
                artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3}))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("after 3 attempts");

            // Should have tried 3 times (MAX_RETRIES)
            verify(mockStorage, times(3)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("does not retry on non-retryable errors (permissions)")
        void doesNotRetryOnPermissionError() {
            // Given - permission error (non-retryable)
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(403, "Access denied"));

            // When/Then
            assertThatThrownBy(() ->
                artifactStorage.saveScreenshot("test-run-123", 0, new byte[]{1, 2, 3}))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to save screenshot");

            // Should NOT have retried - only 1 attempt
            verify(mockStorage, times(1)).create(any(BlobInfo.class), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("Report Upload Retry")
    class ReportUploadRetry {

        @Test
        @DisplayName("retries report upload on broken pipe")
        void retriesReportUploadOnBrokenPipe() {
            // Given - broken pipe is a retryable error
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Broken pipe"))
                    .thenReturn(null);

            // When
            artifactStorage.saveReport("test-run-123", "report.pdf", new byte[]{1, 2, 3});

            // Then
            verify(mockStorage, times(2)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("retries on timeout error message")
        void retriesOnTimeoutError() {
            // Given - "timeout" in message is retryable
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Read timeout occurred"))
                    .thenThrow(new StorageException(0, "Connection timeout"))
                    .thenReturn(null);

            // When
            artifactStorage.saveReport("test-run-123", "report.pdf", new byte[]{1, 2, 3});

            // Then - should have retried twice
            verify(mockStorage, times(3)).create(any(BlobInfo.class), any(byte[].class));
        }

        @Test
        @DisplayName("retries on socket closed error")
        void retriesOnSocketClosed() {
            // Given
            when(mockStorage.create(any(BlobInfo.class), any(byte[].class)))
                    .thenThrow(new StorageException(0, "Socket closed unexpectedly"))
                    .thenReturn(null);

            // When
            artifactStorage.saveReport("test-run-123", "report.pdf", new byte[]{1, 2, 3});

            // Then
            verify(mockStorage, times(2)).create(any(BlobInfo.class), any(byte[].class));
        }
    }
}
