package com.ai2qa.application.port;

/**
 * Provider interface for browser mode configuration.
 *
 * <p>This interface allows the application layer to access browser configuration
 * without depending on the boot layer.
 */
public interface BrowserModeProvider {

    /**
     * Returns true if aria snapshot mode is effectively enabled.
     * This considers the kill-switch, engine type, and snapshot mode settings.
     */
    boolean isAriaEffectivelyEnabled();

    /**
     * Returns the browser engine being used (puppeteer or playwright).
     */
    String getEngine();

    /**
     * Returns the effective snapshot mode (legacy or aria).
     */
    String getEffectiveSnapshotMode();
}
