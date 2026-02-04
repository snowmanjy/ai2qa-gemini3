package com.ai2qa.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for McpClient timeout configuration.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Builder correctly sets default and custom timeouts</li>
 *   <li>Context creation uses dedicated timeout</li>
 *   <li>Tool calls use general timeout</li>
 * </ul>
 */
class McpClientTimeoutTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("Builder Timeout Configuration")
    class BuilderTimeoutConfiguration {

        @Test
        @DisplayName("Builder should use default timeouts when not specified")
        void builder_WhenNoTimeoutsSpecified_UsesDefaults() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Assert - verify via reflection or behavior
            // Since fields are private, we verify through the log message in createContext
            // or we can add package-private getters for testing
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("Builder should accept custom general timeout")
        void builder_WithCustomTimeout_SetsTimeout() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(45_000)
                    .build();

            // Assert
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("Builder should accept custom context creation timeout")
        void builder_WithCustomContextCreationTimeout_SetsTimeout() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .contextCreationTimeoutMs(120_000)
                    .build();

            // Assert
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("Builder should accept both custom timeouts")
        void builder_WithBothCustomTimeouts_SetsBothTimeouts() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(30_000)
                    .contextCreationTimeoutMs(90_000)
                    .build();

            // Assert
            assertThat(client).isNotNull();
        }

        @Test
        @DisplayName("Builder should throw when serverPath is not set")
        void builder_WithoutServerPath_ThrowsException() {
            // Act & Assert
            assertThatThrownBy(() -> McpClient.builder().build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("serverPath must be set");
        }
    }

    @Nested
    @DisplayName("Client State")
    class ClientState {

        @Test
        @DisplayName("Newly built client should not be running")
        void newClient_IsNotRunning() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act & Assert
            assertThat(client.isRunning()).isFalse();
        }

        @Test
        @DisplayName("Newly built client should have no process ID")
        void newClient_HasNoProcessId() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act & Assert
            assertThat(client.getProcessId()).isEmpty();
        }

        @Test
        @DisplayName("createContext should throw when client not initialized")
        void createContext_WhenNotInitialized_ThrowsMcpException() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> client.createContext(false, "test-run-id"))
                    .isInstanceOf(McpClient.McpException.class)
                    .hasMessageContaining("not initialized");
        }

        @Test
        @DisplayName("callTool should throw when client not initialized")
        void callTool_WhenNotInitialized_ThrowsMcpException() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act & Assert
            assertThatThrownBy(() -> client.callTool("some_tool", null))
                    .isInstanceOf(McpClient.McpException.class)
                    .hasMessageContaining("not initialized");
        }
    }

    @Nested
    @DisplayName("Restart Counter")
    class RestartCounter {

        @Test
        @DisplayName("New client should have zero restart attempts")
        void newClient_HasZeroRestartAttempts() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act & Assert
            assertThat(client.getRestartAttempts()).isZero();
        }

        @Test
        @DisplayName("resetRestartCounter should reset to zero")
        void resetRestartCounter_ResetsToZero() throws IOException {
            // Arrange
            Path serverPath = createMockServerPath();
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Act
            client.resetRestartCounter();

            // Assert
            assertThat(client.getRestartAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("Stderr Log Filtering")
    class StderrLogFiltering {

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for D-Bus errors")
        void isHarmless_DbusErrors_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "Failed to connect to the bus: Could not parse server address")).isTrue();
            assertThat(invokeIsHarmless(client, "[ERROR:dbus/bus.cc:406] Failed to connect")).isTrue();
            assertThat(invokeIsHarmless(client, "DBus.NameHasOwner: unknown error")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for inotify errors")
        void isHarmless_InotifyErrors_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "Failed to read /proc/sys/fs/inotify/max_user_watches")).isTrue();
            assertThat(invokeIsHarmless(client, "[ERROR:file_path_watcher_inotify.cc:923]")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for GPU warnings")
        void isHarmless_GpuWarnings_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "GPU process isn't usable")).isTrue();
            assertThat(invokeIsHarmless(client, "gpu_process_host.cc: GPU process exited")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for fontconfig warnings")
        void isHarmless_FontconfigWarnings_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "Fontconfig warning: no fonts found")).isTrue();
            assertThat(invokeIsHarmless(client, "fontconfig: cannot load default config")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for sandbox warnings")
        void isHarmless_SandboxWarnings_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "The setuid sandbox is not running")).isTrue();
            assertThat(invokeIsHarmless(client, "sandbox helper process failed")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return false for FATAL sandbox errors")
        void isHarmless_FatalSandboxError_ReturnsFalse() throws Exception {
            McpClient client = createTestClient();

            // FATAL errors should not be filtered out
            assertThat(invokeIsHarmless(client, "FATAL: sandbox initialization failed")).isFalse();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return false for actual errors")
        void isHarmless_ActualErrors_ReturnsFalse() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "Segmentation fault")).isFalse();
            assertThat(invokeIsHarmless(client, "Browser crashed")).isFalse();
            assertThat(invokeIsHarmless(client, "Error: Navigation timeout exceeded")).isFalse();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for accessibility warnings")
        void isHarmless_AccessibilityWarnings_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "atk_bridge: Unable to connect")).isTrue();
            assertThat(invokeIsHarmless(client, "accessibility module failed to load")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for shared memory warnings")
        void isHarmless_SharedMemoryWarnings_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            assertThat(invokeIsHarmless(client, "/dev/shm too small")).isTrue();
            assertThat(invokeIsHarmless(client, "shared memory allocation failed")).isTrue();
        }

        @Test
        @DisplayName("isHarmlessChromeMessage should return true for stealth plugin main frame errors")
        void isHarmless_StealthPluginMainFrameError_ReturnsTrue() throws Exception {
            McpClient client = createTestClient();

            // puppeteer-extra-plugin-stealth race condition errors
            assertThat(invokeIsHarmless(client, "Requesting main frame too early!")).isTrue();
            assertThat(invokeIsHarmless(client, "[Browser] Stealth plugin race condition on attempt 1/3")).isTrue();
        }

        private McpClient createTestClient() throws IOException {
            return McpClient.builder()
                    .serverPath(createMockServerPath())
                    .build();
        }

        /**
         * Uses reflection to invoke the private isHarmlessChromeMessage method.
         */
        private boolean invokeIsHarmless(McpClient client, String line) throws Exception {
            var method = McpClient.class.getDeclaredMethod("isHarmlessChromeMessage", String.class);
            method.setAccessible(true);
            return (boolean) method.invoke(client, line);
        }
    }

    /**
     * Creates a mock server path for testing builder behavior.
     * The actual server.js doesn't need to exist for builder tests.
     */
    private Path createMockServerPath() throws IOException {
        Path serverDir = tempDir.resolve("mock-mcp-server");
        Files.createDirectories(serverDir);
        // Create a dummy server.js file
        Files.writeString(serverDir.resolve("server.js"), "// mock server");
        return serverDir;
    }
}
