package com.ai2qa.application.port;

import java.util.Map;

/**
 * Port for browser automation drivers (MCP, Playwright, etc.).
 */
public interface BrowserDriverPort {

    /**
     * Starts the browser driver.
     */
    void start();

    /**
     * Checks whether the browser driver is running.
     */
    boolean isRunning();

    /**
     * Creates a new browser context.
     */
    void createContext(boolean recordVideo, String testRunId);

    /**
     * Closes the current browser context.
     */
    void closeContext();

    /**
     * Forces a restart of the browser driver.
     *
     * <p>
     * Used as a fallback when graceful cleanup fails.
     */
    void forceRestart();

    /**
     * Executes a browser tool/action.
     *
     * @param toolName   Name of the tool (e.g., "navigate_page", "click", "fill")
     * @param parameters Tool parameters
     * @return Result map containing tool output
     */
    Map<String, Object> callTool(String toolName, Map<String, Object> parameters);

    /**
     * Shuts down the browser driver and releases resources.
     */
    void close();
}
