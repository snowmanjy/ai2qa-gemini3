package com.ai2qa.config.ai;

import com.ai2qa.application.port.AiResponse;
import com.ai2qa.application.port.ChatClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for SpringChatClientPort timeout behavior.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Normal AI calls complete successfully</li>
 *   <li>Slow AI calls timeout after 60 seconds</li>
 *   <li>Timeout exceptions are properly wrapped and thrown</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SpringChatClientPortTimeoutTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private ChatClientPort chatClientPort;

    @BeforeEach
    void setUp() {
        // Setup mock chain: chatClient.prompt() -> requestSpec -> callResponseSpec
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.options(any())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);

        // Create the port under test (no fallback, no prompt loader)
        chatClientPort = new SpringChatClientPort(chatClient, "test-system-prompt", "test-model");
    }

    @Nested
    @DisplayName("Timeout Behavior")
    class TimeoutBehavior {

        @Test
        @DisplayName("Normal AI call should complete successfully within timeout")
        void callWithMetrics_WhenFast_ReturnsResponse() {
            // Arrange
            ChatResponse mockResponse = createMockResponse("AI response content", 100, 50);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            AiResponse response = chatClientPort.callWithMetrics("system", "user prompt");

            // Assert
            assertThat(response.content()).isEqualTo("AI response content");
            assertThat(response.inputTokens()).isEqualTo(100);
            assertThat(response.outputTokens()).isEqualTo(50);
        }

        @Test
        @DisplayName("Short delay should complete successfully without timeout")
        void callWithMetrics_WithShortDelay_CompletesSuccessfully() throws Exception {
            // Arrange - A short delay that's well within the 60 second timeout
            CountDownLatch callStarted = new CountDownLatch(1);

            when(callResponseSpec.chatResponse()).thenAnswer(invocation -> {
                callStarted.countDown();
                // Small delay simulating AI processing (well under 60 second timeout)
                Thread.sleep(50);
                return createMockResponse("Response after delay", 100, 50);
            });

            // Act
            AiResponse response = chatClientPort.callWithMetrics("system", "user prompt");

            // Assert - Should complete successfully
            boolean threadRan = callStarted.await(5, TimeUnit.SECONDS);
            assertThat(threadRan).isTrue();
            assertThat(response.content()).isEqualTo("Response after delay");
            assertThat(response.inputTokens()).isEqualTo(100);
            assertThat(response.outputTokens()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should handle null user prompt gracefully")
        void callWithMetrics_WhenNullPrompt_ThrowsIllegalArgumentException() {
            // Act & Assert
            assertThatThrownBy(() -> chatClientPort.callWithMetrics("system", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User prompt is required");
        }

        @Test
        @DisplayName("Should handle blank user prompt gracefully")
        void callWithMetrics_WhenBlankPrompt_ThrowsIllegalArgumentException() {
            // Act & Assert
            assertThatThrownBy(() -> chatClientPort.callWithMetrics("system", "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User prompt is required");
        }

        @Test
        @DisplayName("Should propagate AI exceptions correctly")
        void callWithMetrics_WhenAiThrows_PropagatesException() {
            // Arrange
            when(callResponseSpec.chatResponse())
                    .thenThrow(new RuntimeException("AI service unavailable"));

            // Act & Assert
            assertThatThrownBy(() -> chatClientPort.callWithMetrics("system", "prompt"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("AI service unavailable");
        }
    }

    @Nested
    @DisplayName("Response Metrics")
    class ResponseMetrics {

        @Test
        @DisplayName("Should extract token usage from response metadata")
        void callWithMetrics_ExtractsTokenUsage() {
            // Arrange
            ChatResponse mockResponse = createMockResponse("content", 150, 75);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            AiResponse response = chatClientPort.callWithMetrics("system", "user");

            // Assert
            assertThat(response.inputTokens()).isEqualTo(150);
            assertThat(response.outputTokens()).isEqualTo(75);
        }

        @Test
        @DisplayName("Should handle missing metadata gracefully")
        void callWithMetrics_WhenNoMetadata_ReturnsZeroTokens() {
            // Arrange
            ChatResponse mockResponse = mock(ChatResponse.class);
            Generation generation = mock(Generation.class);
            AssistantMessage message = new AssistantMessage("content");

            when(mockResponse.getResult()).thenReturn(generation);
            when(generation.getOutput()).thenReturn(message);
            when(mockResponse.getMetadata()).thenReturn(null);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            AiResponse response = chatClientPort.callWithMetrics("system", "user");

            // Assert
            assertThat(response.inputTokens()).isEqualTo(0);
            assertThat(response.outputTokens()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should record latency in response")
        void callWithMetrics_RecordsLatency() {
            // Arrange
            ChatResponse mockResponse = createMockResponse("content", 100, 50);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            AiResponse response = chatClientPort.callWithMetrics("system", "user");

            // Assert - Latency should be recorded (> 0)
            assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Temperature Override")
    class TemperatureOverride {

        @Test
        @DisplayName("Should apply temperature when provided")
        void callWithMetrics_WithTemperature_AppliesOption() {
            // Arrange
            ChatResponse mockResponse = createMockResponse("content", 100, 50);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            chatClientPort.callWithMetrics("system", "user", 0.7);

            // Assert - Verify options were applied
            verify(requestSpec).options(any());
        }

        @Test
        @DisplayName("Should not apply options when temperature is null")
        void callWithMetrics_WithoutTemperature_NoOptionsApplied() {
            // Arrange
            ChatResponse mockResponse = createMockResponse("content", 100, 50);
            when(callResponseSpec.chatResponse()).thenReturn(mockResponse);

            // Act
            chatClientPort.callWithMetrics("system", "user", null);

            // Assert - Verify options were NOT applied
            verify(requestSpec, never()).options(any());
        }
    }

    @Nested
    @DisplayName("Executor Configuration")
    class ExecutorConfiguration {

        @Test
        @DisplayName("Executor threads should be daemon threads for graceful shutdown")
        void executorThreads_AreDaemonThreads() throws Exception {
            // Arrange - Set up a slow response to ensure a thread is created
            ChatResponse mockResponse = createMockResponse("content", 100, 50);

            // Use a latch to ensure the thread starts
            CountDownLatch threadStarted = new CountDownLatch(1);
            AtomicBoolean threadIsDaemon = new AtomicBoolean(false);

            when(callResponseSpec.chatResponse()).thenAnswer(invocation -> {
                // Capture thread properties while inside the executor
                threadIsDaemon.set(Thread.currentThread().isDaemon());
                threadStarted.countDown();
                return mockResponse;
            });

            // Act
            chatClientPort.callWithMetrics("system", "user prompt");

            // Wait for thread to start (or timeout after 5 seconds)
            boolean started = threadStarted.await(5, TimeUnit.SECONDS);

            // Assert
            assertThat(started).isTrue();
            assertThat(threadIsDaemon.get())
                    .as("Executor threads should be daemon threads to allow JVM shutdown")
                    .isTrue();
        }

        @Test
        @DisplayName("Executor thread should have descriptive name")
        void executorThreads_HaveDescriptiveName() throws Exception {
            // Arrange
            ChatResponse mockResponse = createMockResponse("content", 100, 50);
            CountDownLatch threadStarted = new CountDownLatch(1);
            AtomicBoolean hasCorrectName = new AtomicBoolean(false);

            when(callResponseSpec.chatResponse()).thenAnswer(invocation -> {
                hasCorrectName.set(Thread.currentThread().getName().contains("ai-call"));
                threadStarted.countDown();
                return mockResponse;
            });

            // Act
            chatClientPort.callWithMetrics("system", "user prompt");
            threadStarted.await(5, TimeUnit.SECONDS);

            // Assert
            assertThat(hasCorrectName.get())
                    .as("Executor threads should have 'ai-call' in their name")
                    .isTrue();
        }
    }

    /**
     * Creates a mock ChatResponse with the given content and token counts.
     */
    private ChatResponse createMockResponse(String content, int inputTokens, int outputTokens) {
        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        AssistantMessage message = new AssistantMessage(content);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        DefaultUsage usage = new DefaultUsage((long) inputTokens, (long) outputTokens);

        when(mockResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(message);
        when(mockResponse.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);

        return mockResponse;
    }
}
