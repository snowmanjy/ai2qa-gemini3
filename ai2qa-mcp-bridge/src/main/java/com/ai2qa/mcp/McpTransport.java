package com.ai2qa.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles stdio read/write for JSON-RPC communication with MCP server.
 *
 * Thread-safe implementation with separate reader thread for async message handling.
 */
public class McpTransport implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpTransport.class);

    private final ObjectMapper objectMapper;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final ConcurrentHashMap<Long, CompletableFuture<McpMessage.Response>> pendingRequests;
    private final Thread readerThread;

    private volatile boolean running = true;

    /**
     * Factory method to create transport from process streams.
     *
     * @param inputStream  Process stdout (we read from here)
     * @param outputStream Process stdin (we write here)
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @return Configured McpTransport instance
     */
    public static McpTransport create(
            InputStream inputStream,
            OutputStream outputStream,
            ObjectMapper objectMapper
    ) {
        return new McpTransport(inputStream, outputStream, objectMapper);
    }

    private McpTransport(
            InputStream inputStream,
            OutputStream outputStream,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.pendingRequests = new ConcurrentHashMap<>();

        // Start background reader thread
        this.readerThread = new Thread(this::readLoop, "mcp-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    /**
     * Sends a JSON-RPC request to the MCP server and registers it for response correlation.
     *
     * @param request The request to send
     * @return CompletableFuture that will be completed when the response arrives
     * @throws McpTransportException if serialization or writing fails
     */
    public CompletableFuture<McpMessage.Response> send(McpMessage.Request request) {
        // Register the pending request BEFORE sending to avoid race condition
        CompletableFuture<McpMessage.Response> future = new CompletableFuture<>();
        pendingRequests.put(request.id(), future);

        try {
            String json = objectMapper.writeValueAsString(request);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
            log.debug("Sent request: id={}, method={}", request.id(), request.method());
            return future;
        } catch (JsonProcessingException e) {
            pendingRequests.remove(request.id());
            throw new McpTransportException("Failed to serialize request", e);
        } catch (IOException e) {
            pendingRequests.remove(request.id());
            throw new McpTransportException("Failed to write request", e);
        }
    }

    /**
     * Sends a JSON-RPC notification to the MCP server.
     *
     * @param notification The notification to send
     * @throws McpTransportException if serialization or writing fails
     */
    public void send(McpMessage.Notification notification) {
        try {
            String json = objectMapper.writeValueAsString(notification);
            synchronized (writer) {
                writer.write(json);
                writer.newLine();
                writer.flush();
            }
            log.debug("Sent notification: method={}", notification.method());
        } catch (JsonProcessingException e) {
            throw new McpTransportException("Failed to serialize notification", e);
        } catch (IOException e) {
            throw new McpTransportException("Failed to write notification", e);
        }
    }

    /**
     * Waits for a response to a specific request with the specified timeout.
     *
     * @param requestId The request ID to wait for
     * @param timeoutMs Timeout in milliseconds
     * @return Optional containing the response, or empty if timeout
     */
    public Optional<McpMessage.Response> receive(long requestId, long timeoutMs) {
        CompletableFuture<McpMessage.Response> future = pendingRequests.get(requestId);
        if (future == null) {
            log.warn("No pending request found for id={}", requestId);
            return Optional.empty();
        }

        try {
            McpMessage.Response response = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return Optional.of(response);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("Timeout waiting for response to request id={}", requestId);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Error waiting for response to request id={}: {}", requestId, e.getCause().getMessage());
            return Optional.empty();
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * Legacy method for backwards compatibility - waits for any response.
     * @deprecated Use receive(requestId, timeoutMs) instead
     */
    @Deprecated
    public Optional<McpMessage.Response> receive(long timeoutMs) {
        // For backwards compatibility, wait a bit and check if any response arrived
        // This is only used during initialization before proper request tracking
        try {
            Thread.sleep(Math.min(timeoutMs, 100));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Return any completed future (used for init sequence)
        return pendingRequests.values().stream()
                .filter(CompletableFuture::isDone)
                .findFirst()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        return null;
                    }
                });
    }

    /**
     * Background loop that reads responses from the MCP server and dispatches to waiting callers.
     */
    private void readLoop() {
        while (running) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    log.info("MCP server closed connection");
                    break;
                }

                if (line.isBlank()) {
                    continue;
                }

                log.debug("Received: {}", line);

                McpMessage.Response response = objectMapper.readValue(line, McpMessage.Response.class);

                // Find and complete the pending request by ID
                CompletableFuture<McpMessage.Response> future = pendingRequests.get(response.id());
                if (future != null) {
                    future.complete(response);
                    log.debug("Dispatched response for request id={}", response.id());
                } else {
                    log.warn("Received response for unknown request id={}", response.id());
                }

            } catch (IOException e) {
                if (running) {
                    log.error("Error reading from MCP server", e);
                }
                break;
            }
        }

        // Complete any pending requests with errors on shutdown
        pendingRequests.forEach((id, future) -> {
            if (!future.isDone()) {
                future.completeExceptionally(new McpTransportException("Transport closed", null));
            }
        });
    }

    @Override
    public void close() {
        running = false;
        readerThread.interrupt();

        try {
            writer.close();
        } catch (IOException e) {
            log.warn("Error closing writer", e);
        }

        try {
            reader.close();
        } catch (IOException e) {
            log.warn("Error closing reader", e);
        }
    }

    /**
     * Exception thrown when transport operations fail.
     */
    public static class McpTransportException extends RuntimeException {
        public McpTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
