package com.ai2qa.mcp;

import com.ai2qa.application.port.BrowserDriverPort;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * MCP Client that manages Node.js subprocess lifecycle and JSON-RPC
 * communication.
 *
 * <p>
 * Usage:
 * 
 * <pre>{@code
 * try (McpClient client = McpClient.builder()
 *         .serverPath(Path.of("path/to/mcp-server"))
 *         .build()) {
 *     client.start();
 *     var response = client.callTool("navigate_page", Map.of("url", "https://google.com"));
 * }
 * }</pre>
 */
public class McpClient implements BrowserDriverPort, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private static final long DEFAULT_TIMEOUT_MS = 60_000;
    private static final long DEFAULT_CONTEXT_CREATION_TIMEOUT_MS = 90_000;
    private static final long INIT_TIMEOUT_MS = 60_000;
    private static final long WATCHDOG_INTERVAL_MS = 5_000;
    private static final int MAX_RESTART_ATTEMPTS = 3;
    private static final long RESTART_COOLDOWN_MS = 10_000;

    private final Path serverPath;
    private final ObjectMapper objectMapper;
    private final long timeoutMs;
    private final long contextCreationTimeoutMs;
    private final LongConsumer sleeper;
    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final Map<Long, Object> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicInteger restartAttempts = new AtomicInteger(0);
    private final Object restartLock = new Object();

    // Browser configuration
    private final String browserEngine;
    private final String snapshotMode;
    private final boolean ariaEnabled;
    private final boolean fallbackEnabled;

    private Process process;
    private ProcessHandle processHandle;
    private McpTransport transport;
    private volatile boolean initialized = false;
    private volatile boolean shutdownRequested = false;
    private volatile long lastRestartTime = 0;

    // Watchdog scheduler
    private ScheduledExecutorService watchdogExecutor;
    private ScheduledFuture<?> watchdogTask;

    private McpClient(Builder builder) {
        this.serverPath = builder.serverPath;
        this.timeoutMs = builder.timeoutMs;
        this.contextCreationTimeoutMs = builder.contextCreationTimeoutMs;
        this.sleeper = builder.sleeper;
        this.browserEngine = builder.browserEngine;
        this.snapshotMode = builder.snapshotMode;
        this.ariaEnabled = builder.ariaEnabled;
        this.fallbackEnabled = builder.fallbackEnabled;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Creates a new builder for McpClient.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Starts the MCP server subprocess and initializes the connection.
     * Also starts the watchdog to monitor process health.
     *
     * @throws McpException if starting the server fails
     */
    public void start() {
        synchronized (restartLock) {
            if (process != null && process.isAlive()) {
                log.warn("MCP server already running");
                return;
            }

            shutdownRequested = false;
            startInternal();
            startWatchdog();
        }
    }

    /**
     * Internal method to start the MCP server process.
     */
    private void startInternal() {
        try {
            log.info("Starting MCP server from: {}", serverPath);

            ProcessBuilder processBuilder = new ProcessBuilder()
                    .command("node", serverPath.resolve("server.js").toString())
                    .directory(serverPath.toFile())
                    .redirectErrorStream(false);

            // Inherit environment but ensure Node.js can find modules
            processBuilder.environment().put("NODE_ENV", "production");

            process = processBuilder.start();
            processHandle = process.toHandle();

            // Capture stderr in a separate thread for debugging
            startErrorStreamReader();

            transport = McpTransport.create(
                    process.getInputStream(),
                    process.getOutputStream(),
                    objectMapper);

            // Wait for server initialization
            initialize();

            // Reset restart counter on successful start
            restartAttempts.set(0);
            lastRestartTime = System.currentTimeMillis();

            log.info("MCP server started successfully (PID: {})", processHandle.pid());

        } catch (IOException e) {
            throw new McpException("Failed to start MCP server", e);
        }
    }

    /**
     * Starts the watchdog thread to monitor process health.
     */
    private void startWatchdog() {
        if (watchdogExecutor != null && !watchdogExecutor.isShutdown()) {
            return; // Watchdog already running
        }

        watchdogExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-watchdog");
            t.setDaemon(true);
            return t;
        });

        watchdogTask = watchdogExecutor.scheduleAtFixedRate(
                this::checkProcessHealth,
                WATCHDOG_INTERVAL_MS,
                WATCHDOG_INTERVAL_MS,
                TimeUnit.MILLISECONDS);

        log.info("MCP watchdog started (interval: {}ms)", WATCHDOG_INTERVAL_MS);
    }

    /**
     * Watchdog task that checks process health and attempts recovery.
     */
    private void checkProcessHealth() {
        if (shutdownRequested) {
            return;
        }

        boolean isAlive = processHandle != null && processHandle.isAlive();

        if (!isAlive && initialized) {
            log.warn("MCP server process died unexpectedly");
            handleProcessDeath();
        } else if (isAlive && !initialized) {
            log.debug("MCP process alive but not initialized - may be starting up");
        }
    }

    /**
     * Handles unexpected process death with automatic restart.
     */
    private void handleProcessDeath() {
        synchronized (restartLock) {
            if (shutdownRequested) {
                return;
            }

            initialized = false;

            // Check cooldown
            long timeSinceLastRestart = System.currentTimeMillis() - lastRestartTime;
            if (timeSinceLastRestart < RESTART_COOLDOWN_MS) {
                log.warn("Restart cooldown active, waiting {}ms",
                        RESTART_COOLDOWN_MS - timeSinceLastRestart);
                return;
            }

            int attempts = restartAttempts.incrementAndGet();
            if (attempts > MAX_RESTART_ATTEMPTS) {
                log.error("Max restart attempts ({}) exceeded. MCP server will not be restarted.",
                        MAX_RESTART_ATTEMPTS);
                return;
            }

            log.info("Attempting to restart MCP server (attempt {}/{})", attempts, MAX_RESTART_ATTEMPTS);

            try {
                // Clean up old resources
                cleanupProcess();

                // Restart
                startInternal();
                log.info("MCP server restarted successfully");

            } catch (Exception e) {
                log.error("Failed to restart MCP server: {}", e.getMessage());
            }
        }
    }

    /**
     * Manually forces a restart of the MCP server process.
     * Useful for recovering from unrecoverable states (e.g., stuck browser).
     */
    public void forceRestart() {
        log.warn("Forcing MCP server restart requested by application");
        // Reset counters so manual restart doesn't get blocked by auto-restart limits
        // immediately
        // if appropriate, or let it share limits.
        // For manual force, we typically bypass cooldowns/limits or reset them.
        resetRestartCounter();

        synchronized (restartLock) {
            cleanupProcess();
            // Wait brief moment before restart
            sleeper.accept(1000);
            startInternal();
        }
    }

    /**
     * Cleans up process resources without shutting down watchdog.
     */
    private void cleanupProcess() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception e) {
                log.debug("Error closing transport: {}", e.getMessage());
            }
            transport = null;
        }

        if (process != null) {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(2, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            process = null;
            processHandle = null;
        }
    }

    /**
     * Performs MCP initialization handshake.
     */
    private void initialize() {
        // Build browser config for MCP server
        Map<String, Object> browserConfig = Map.of(
                "engine", browserEngine,
                "snapshotMode", snapshotMode,
                "ariaEnabled", ariaEnabled,
                "fallbackEnabled", fallbackEnabled
        );

        // Send initialize request with browser configuration
        long initRequestId = nextRequestId();
        var initRequest = new McpMessage.Request(
                initRequestId,
                "initialize",
                Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                                "name", "ai2qa-mcp-client",
                                "version", "1.0.0"),
                        "browserConfig", browserConfig));

        transport.send(initRequest);

        Optional<McpMessage.Response> response = transport.receive(initRequestId, INIT_TIMEOUT_MS);
        if (response.isEmpty()) {
            throw new McpException("Timeout waiting for initialize response");
        }

        if (response.get().isError()) {
            throw new McpException("Initialize failed: " + response.get().error().message());
        }

        // Send initialized notification
        transport.send(new McpMessage.Notification("notifications/initialized", null));

        initialized = true;
        log.info("MCP protocol initialized with browser config: engine={}, snapshotMode={}, ariaEnabled={}",
                browserEngine, snapshotMode, ariaEnabled);
    }

    /**
     * Creates a new isolated browser context.
     *
     * <p>Uses a dedicated longer timeout for context creation since launching
     * Chrome in containerized environments (Cloud Run, Docker) can be slow,
     * especially during cold starts.
     *
     * @param videoEnabled Whether to enable video recording (placeholder for
     *                     future)
     * @param runId        The Test Run ID for tracking
     */
    public void createContext(boolean videoEnabled, String runId) {
        log.info("Creating browser context for run: {}, video={}, timeout={}ms",
                runId, videoEnabled, contextCreationTimeoutMs);
        ensureInitialized();

        // This maps to 'browser/createContext' custom method on server
        Map<String, Object> params = Map.of(
                "video", videoEnabled,
                "runId", runId);

        // Use dedicated longer timeout for context creation (Chrome startup can be slow)
        callCustomMethod("browser/createContext", params, contextCreationTimeoutMs);
    }

    /**
     * Closes the current browser context, wiping ephemeral state.
     */
    public void closeContext() {
        log.info("Closing browser context");
        if (isRunning()) {
            try {
                callCustomMethod("browser/closeContext", Map.of());
            } catch (Exception e) {
                log.warn("Failed to close context gracefully: {}", e.getMessage());
                // If graceful close fails, caller might force restart
                throw e;
            }
        }
    }

    /**
     * Calls a custom JSON-RPC method (not a tool) with the default timeout.
     */
    private void callCustomMethod(String method, Map<String, Object> params) {
        callCustomMethod(method, params, timeoutMs);
    }

    /**
     * Calls a custom JSON-RPC method (not a tool) with a custom timeout.
     *
     * @param method    The JSON-RPC method name
     * @param params    Method parameters
     * @param customTimeoutMs Custom timeout in milliseconds
     */
    private void callCustomMethod(String method, Map<String, Object> params, long customTimeoutMs) {
        try {
            CompletableFuture.supplyAsync(() -> {
                long requestId = nextRequestId();
                var request = new McpMessage.Request(
                        requestId,
                        method,
                        params != null ? params : Map.of());

                transport.send(request);

                Optional<McpMessage.Response> response = transport.receive(requestId, customTimeoutMs);
                if (response.isEmpty()) {
                    throw new McpException("No response received for method: " + method);
                }

                McpMessage.Response resp = response.get();
                if (resp.isError()) {
                    throw new McpException("Method call failed: " + resp.error().message());
                }

                return resp.result();
            }).orTimeout(customTimeoutMs, TimeUnit.MILLISECONDS).join();

        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException) {
                throw (McpException) cause;
            } else {
                throw new McpException("Method " + method + " failed: " + cause.getMessage(), cause);
            }
        }
    }

    /**
     * Calls an MCP tool with the given parameters.
     * Uses CompletableFuture with timeout for robust error handling.
     *
     * <p>Timeout hierarchy (outer must be > inner to avoid race conditions):
     * <ul>
     *   <li>CompletableFuture timeout: timeoutMs + 10s (outermost, safety net)</li>
     *   <li>Transport receive timeout: timeoutMs + 5s (allows server to return errors)</li>
     *   <li>Server-side operation timeout: timeoutMs (actual Playwright/browser timeout)</li>
     * </ul>
     *
     * @param toolName   Name of the tool to call
     * @param parameters Tool parameters
     * @return Tool result as a Map
     * @throws McpException if the call fails or times out
     */
    public Map<String, Object> callTool(String toolName, Map<String, Object> parameters) {
        ensureInitialized();

        // Outer timeout must be greater than transport timeout (timeoutMs + 5000)
        // to let transport timeout first and return proper error messages
        long futureTimeoutMs = timeoutMs + 10_000;

        try {
            return callToolAsync(toolName, parameters)
                    .orTimeout(futureTimeoutMs, TimeUnit.MILLISECONDS)
                    .join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                log.error("Tool call timed out after {}ms: {}", futureTimeoutMs, toolName);
                throw new McpException("Timeout waiting for tool response: " + toolName);
            } else if (cause instanceof McpException) {
                throw (McpException) cause;
            } else {
                throw new McpException("Tool call failed: " + cause.getMessage(), cause);
            }
        }
    }

    /**
     * Asynchronous tool call implementation.
     *
     * @param toolName   Name of the tool to call
     * @param parameters Tool parameters
     * @return CompletableFuture with the result
     */
    private CompletableFuture<Map<String, Object>> callToolAsync(
            String toolName, Map<String, Object> parameters) {

        return CompletableFuture.supplyAsync(() -> {
            long requestId = nextRequestId();
            var request = new McpMessage.Request(
                    requestId,
                    "tools/call",
                    Map.of(
                            "name", toolName,
                            "arguments", parameters != null ? parameters : Map.of()));

            transport.send(request);

            // Use request ID-based correlation to get the correct response
            // Give extra 5 seconds buffer for server-side operations to complete and return errors
            // This prevents the client from timing out before receiving the actual error message
            Optional<McpMessage.Response> response = transport.receive(requestId, timeoutMs + 5000);
            if (response.isEmpty()) {
                throw new McpException("No response received for tool: " + toolName);
            }

            McpMessage.Response resp = response.get();
            if (resp.isError()) {
                throw new McpException("Tool call failed: " + resp.error().message());
            }

            return extractResult(resp);
        });
    }

    /**
     * Lists available tools from the MCP server.
     *
     * @return List of tool definitions
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listTools() {
        ensureInitialized();

        long requestId = nextRequestId();
        var request = new McpMessage.Request(
                requestId,
                "tools/list",
                null);

        transport.send(request);

        Optional<McpMessage.Response> response = transport.receive(requestId, timeoutMs);
        if (response.isEmpty()) {
            throw new McpException("Timeout waiting for tools/list response");
        }

        if (response.get().isError()) {
            throw new McpException("Failed to list tools: " + response.get().error().message());
        }

        Map<String, Object> result = extractResult(response.get());
        return (List<Map<String, Object>>) result.getOrDefault("tools", List.of());
    }

    /**
     * Extracts the result from a successful response.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractResult(McpMessage.Response response) {
        Object result = response.result();
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }
        return Map.of("result", result);
    }

    /**
     * Ensures the client is initialized before making calls.
     */
    private void ensureInitialized() {
        if (!initialized) {
            throw new McpException("MCP client not initialized. Call start() first.");
        }
        if (process == null || !process.isAlive()) {
            throw new McpException("MCP server process is not running");
        }
    }

    /**
     * Generates the next request ID.
     */
    private long nextRequestId() {
        return requestIdCounter.getAndIncrement();
    }

    /**
     * Starts a thread to read and log stderr from the Node.js process.
     * Filters known harmless Chrome/Chromium messages to reduce log noise.
     */
    private void startErrorStreamReader() {
        Thread errorReader = new Thread(() -> {
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logStderrLine(line);
                }
            } catch (IOException e) {
                log.debug("Error stream closed");
            }
        }, "mcp-error-reader");
        errorReader.setDaemon(true);
        errorReader.start();
    }

    /**
     * Logs stderr lines with appropriate log level based on content.
     *
     * <p>Known harmless Chrome/Chromium messages in containerized environments
     * are logged at DEBUG level to reduce noise. These include:
     * <ul>
     *   <li>D-Bus errors (no D-Bus daemon in containers)</li>
     *   <li>inotify errors (file watcher limits)</li>
     *   <li>GPU/sandbox warnings (no GPU in containers)</li>
     *   <li>Fontconfig warnings</li>
     * </ul>
     */
    private void logStderrLine(String line) {
        // Informational messages from MCP server - log at INFO
        if (line.contains("[Server]") || line.contains("[Browser]") || line.contains("[Playwright]")) {
            log.info("[MCP Server] {}", line);
            return;
        }

        // DevTools ready message - log at INFO
        if (line.contains("DevTools listening")) {
            log.info("[MCP Server] {}", line);
            return;
        }

        // Known harmless Chrome/Chromium messages in containers - log at DEBUG
        if (isHarmlessChromeMessage(line)) {
            log.debug("[MCP Server] {}", line);
            return;
        }

        // Actual errors - log at WARN
        log.warn("[MCP Server] {}", line);
    }

    /**
     * Checks if a Chrome stderr message is known to be harmless in containers.
     */
    private boolean isHarmlessChromeMessage(String line) {
        // Stealth plugin race condition - handled by retry logic in browser.js
        // These messages appear when puppeteer-extra-plugin-stealth tries to inject
        // scripts before the main frame is fully initialized
        if (line.contains("main frame") || line.contains("Stealth plugin race condition")) {
            return true;
        }

        // D-Bus errors - no D-Bus daemon in containers (Cloud Run, Docker)
        if (line.contains("dbus") || line.contains("DBus") || line.contains("Failed to connect to the bus")) {
            return true;
        }

        // inotify errors - file watcher limits in containers
        if (line.contains("inotify") || line.contains("max_user_watches")) {
            return true;
        }

        // GPU process warnings - no GPU in headless containers
        if (line.contains("GPU process") || line.contains("gpu_process") || line.contains("GpuProcess")) {
            return true;
        }

        // Sandbox warnings - common in containers
        if (line.contains("sandbox") && !line.contains("FATAL")) {
            return true;
        }

        // Font configuration warnings
        if (line.contains("Fontconfig") || line.contains("fontconfig")) {
            return true;
        }

        // Shared memory warnings in containers
        if (line.contains("/dev/shm") || line.contains("shared memory")) {
            return true;
        }

        // Network service warnings
        if (line.contains("network_service") || line.contains("NetworkService")) {
            return true;
        }

        // NETLINK socket errors - no permission to track network interfaces in containers
        if (line.contains("NETLINK") || line.contains("address_tracker")) {
            return true;
        }

        // GCM/Push notification errors - not needed for browser automation
        if (line.contains("gcm") || line.contains("registration_request") || line.contains("QUOTA_EXCEEDED")) {
            return true;
        }

        // ALSA audio errors - no sound hardware in containers
        if (line.contains("ALSA") || line.contains("alsa") || line.contains("PcmOpen") || line.contains("snd_")) {
            return true;
        }

        // Accessibility warnings when no display
        if (line.contains("accessibility") || line.contains("atk_bridge")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if the MCP server is running.
     */
    public boolean isRunning() {
        return process != null && process.isAlive() && initialized;
    }

    @Override
    public void close() {
        log.info("Shutting down MCP client...");
        shutdownRequested = true;
        initialized = false;

        // Stop watchdog first
        stopWatchdog();

        // Clean up process and transport
        cleanupProcess();

        log.info("MCP client shutdown complete");
    }

    /**
     * Stops the watchdog executor.
     */
    private void stopWatchdog() {
        if (watchdogTask != null) {
            watchdogTask.cancel(false);
            watchdogTask = null;
        }

        if (watchdogExecutor != null && !watchdogExecutor.isShutdown()) {
            watchdogExecutor.shutdown();
            try {
                if (!watchdogExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    watchdogExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                watchdogExecutor.shutdownNow();
            }
            watchdogExecutor = null;
        }

        log.debug("MCP watchdog stopped");
    }

    /**
     * Resets the restart counter (useful for testing or manual recovery).
     */
    public void resetRestartCounter() {
        restartAttempts.set(0);
        lastRestartTime = 0;
        log.info("MCP restart counter reset");
    }

    /**
     * Gets the current restart attempt count.
     *
     * @return Number of restart attempts since last successful start.
     */
    public int getRestartAttempts() {
        return restartAttempts.get();
    }

    /**
     * Gets the process ID if running.
     *
     * @return Optional containing the PID if the process is running.
     */
    public Optional<Long> getProcessId() {
        return processHandle != null && processHandle.isAlive()
                ? Optional.of(processHandle.pid())
                : Optional.empty();
    }

    /**
     * Builder for McpClient.
     */
    public static class Builder {
        private Path serverPath;
        private long timeoutMs = DEFAULT_TIMEOUT_MS;
        private long contextCreationTimeoutMs = DEFAULT_CONTEXT_CREATION_TIMEOUT_MS;
        private LongConsumer sleeper = McpClient::defaultSleep;

        // Browser configuration with defaults
        private String browserEngine = "puppeteer";
        private String snapshotMode = "auto";
        private boolean ariaEnabled = true;
        private boolean fallbackEnabled = true;

        /**
         * Sets the path to the MCP server directory.
         */
        public Builder serverPath(Path serverPath) {
            this.serverPath = serverPath;
            return this;
        }

        /**
         * Sets the timeout for tool calls in milliseconds.
         */
        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the timeout for browser context creation in milliseconds.
         *
         * <p>Context creation launches Chrome, which can be slow in containerized
         * environments (Cloud Run, Docker), especially during cold starts.
         * Default is 60 seconds.
         */
        public Builder contextCreationTimeoutMs(long contextCreationTimeoutMs) {
            this.contextCreationTimeoutMs = contextCreationTimeoutMs;
            return this;
        }

        /**
         * Sets a custom sleeper for testability.
         */
        public Builder sleeper(LongConsumer sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        /**
         * Sets the browser engine (puppeteer or playwright).
         */
        public Builder browserEngine(String browserEngine) {
            this.browserEngine = browserEngine;
            return this;
        }

        /**
         * Sets the snapshot mode (legacy, aria, or auto).
         */
        public Builder snapshotMode(String snapshotMode) {
            this.snapshotMode = snapshotMode;
            return this;
        }

        /**
         * Sets whether aria snapshots are enabled (kill-switch).
         */
        public Builder ariaEnabled(boolean ariaEnabled) {
            this.ariaEnabled = ariaEnabled;
            return this;
        }

        /**
         * Sets whether fallback to legacy mode is enabled.
         */
        public Builder fallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
            return this;
        }

        /**
         * Builds the McpClient.
         */
        public McpClient build() {
            if (serverPath == null) {
                throw new IllegalStateException("serverPath must be set");
            }
            return new McpClient(this);
        }
    }

    /**
     * Default sleep implementation.
     */
    private static void defaultSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Exception thrown when MCP operations fail.
     */
    public static class McpException extends RuntimeException {
        public McpException(String message) {
            super(message);
        }

        public McpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
