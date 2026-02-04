package com.ai2qa.config;

import com.ai2qa.mcp.McpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MCP Client timeout configuration.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Default timeouts are correctly applied</li>
 *   <li>Custom timeouts from configuration are passed to McpClient</li>
 *   <li>Context creation uses dedicated longer timeout</li>
 * </ul>
 */
@DisplayName("McpClientConfiguration")
class McpClientConfigurationTest {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("McpClient Builder Integration")
    class McpClientBuilderIntegration {

        @Test
        @DisplayName("McpClient should accept both timeout configurations")
        void mcpClient_AcceptsBothTimeoutConfigurations() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();
            long generalTimeout = 30_000;
            long contextCreationTimeout = 90_000;

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(generalTimeout)
                    .contextCreationTimeoutMs(contextCreationTimeout)
                    .build();

            // Assert - verify via reflection that both timeouts are set
            assertThat(getFieldValue(client, "timeoutMs")).isEqualTo(generalTimeout);
            assertThat(getFieldValue(client, "contextCreationTimeoutMs")).isEqualTo(contextCreationTimeout);
        }

        @Test
        @DisplayName("McpClient should use default context creation timeout when not specified")
        void mcpClient_UsesDefaultContextCreationTimeout() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();
            long expectedDefault = 90_000; // DEFAULT_CONTEXT_CREATION_TIMEOUT_MS

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Assert
            assertThat(getFieldValue(client, "contextCreationTimeoutMs")).isEqualTo(expectedDefault);
        }

        @Test
        @DisplayName("McpClient should use default general timeout when not specified")
        void mcpClient_UsesDefaultGeneralTimeout() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();
            long expectedDefault = 60_000; // DEFAULT_TIMEOUT_MS

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .build();

            // Assert
            assertThat(getFieldValue(client, "timeoutMs")).isEqualTo(expectedDefault);
        }

        @Test
        @DisplayName("Context creation timeout should be independent of general timeout")
        void contextCreationTimeout_IsIndependentOfGeneralTimeout() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();
            long generalTimeout = 45_000;
            long contextTimeout = 120_000;

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(generalTimeout)
                    .contextCreationTimeoutMs(contextTimeout)
                    .build();

            // Assert - both should be different
            long actualGeneral = (long) getFieldValue(client, "timeoutMs");
            long actualContext = (long) getFieldValue(client, "contextCreationTimeoutMs");

            assertThat(actualGeneral).isEqualTo(generalTimeout);
            assertThat(actualContext).isEqualTo(contextTimeout);
            assertThat(actualContext).isNotEqualTo(actualGeneral);
        }
    }

    @Nested
    @DisplayName("Timeout Value Validation")
    class TimeoutValueValidation {

        @Test
        @DisplayName("Should allow very short timeout for testing")
        void shouldAllowShortTimeout() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(1_000)
                    .contextCreationTimeoutMs(5_000)
                    .build();

            // Assert
            assertThat(getFieldValue(client, "timeoutMs")).isEqualTo(1_000L);
            assertThat(getFieldValue(client, "contextCreationTimeoutMs")).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("Should allow very long timeout for slow environments")
        void shouldAllowLongTimeout() throws Exception {
            // Arrange
            Path serverPath = createMockServerPath();
            long twoMinutes = 120_000;
            long fiveMinutes = 300_000;

            // Act
            McpClient client = McpClient.builder()
                    .serverPath(serverPath)
                    .timeoutMs(twoMinutes)
                    .contextCreationTimeoutMs(fiveMinutes)
                    .build();

            // Assert
            assertThat(getFieldValue(client, "timeoutMs")).isEqualTo(twoMinutes);
            assertThat(getFieldValue(client, "contextCreationTimeoutMs")).isEqualTo(fiveMinutes);
        }
    }

    /**
     * Creates a mock server path for testing.
     */
    private Path createMockServerPath() throws IOException {
        Path serverDir = tempDir.resolve("mock-mcp-server");
        Files.createDirectories(serverDir);
        Files.writeString(serverDir.resolve("server.js"), "// mock server");
        return serverDir;
    }

    /**
     * Uses reflection to get private field value for testing.
     */
    private Object getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
}
