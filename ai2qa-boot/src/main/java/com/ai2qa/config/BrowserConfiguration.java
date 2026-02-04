package com.ai2qa.config;

import com.ai2qa.application.port.BrowserModeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for browser automation settings.
 *
 * <p>Supports two browser engines:
 * <ul>
 *   <li><b>puppeteer</b> (default): Puppeteer with stealth plugin for anti-detection</li>
 *   <li><b>playwright</b>: Playwright for accessibility tree snapshots</li>
 * </ul>
 *
 * <p>Snapshot modes:
 * <ul>
 *   <li><b>legacy</b>: CSS selector-based snapshots (works with both engines)</li>
 *   <li><b>aria</b>: Accessibility tree snapshots (~95% token savings, requires Playwright)</li>
 *   <li><b>auto</b>: Uses aria if Playwright engine is active, otherwise legacy</li>
 * </ul>
 *
 * <p>Kill-switch: Set {@code aria-enabled=false} to disable aria mode at runtime.
 *
 * <p>Implements {@link BrowserModeProvider} to expose configuration to the application layer.
 */
@Configuration
@ConfigurationProperties(prefix = "ai2qa.browser")
public class BrowserConfiguration implements BrowserModeProvider {

    private static final Logger log = LoggerFactory.getLogger(BrowserConfiguration.class);

    /**
     * Browser engine to use.
     * Options: puppeteer, playwright
     */
    private String engine = "puppeteer";

    /**
     * Snapshot mode for DOM snapshots.
     * Options: legacy, aria, auto
     */
    private String snapshotMode = "auto";

    /**
     * Kill-switch for aria mode.
     * Set to false to disable aria snapshots at runtime.
     */
    private boolean ariaEnabled = true;

    /**
     * Enable fallback to legacy mode if aria fails.
     */
    private boolean fallbackEnabled = true;

    @Override
    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getSnapshotMode() {
        return snapshotMode;
    }

    public void setSnapshotMode(String snapshotMode) {
        this.snapshotMode = snapshotMode;
    }

    public boolean isAriaEnabled() {
        return ariaEnabled;
    }

    public void setAriaEnabled(boolean ariaEnabled) {
        this.ariaEnabled = ariaEnabled;
    }

    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * Determines the effective snapshot mode based on configuration.
     *
     * <p>Logic:
     * <ul>
     *   <li>If aria is disabled (kill-switch), returns "legacy"</li>
     *   <li>If mode is "auto", returns "aria" for playwright engine, "legacy" for puppeteer</li>
     *   <li>Otherwise returns the configured mode</li>
     * </ul>
     *
     * @return The effective snapshot mode to use
     */
    @Override
    public String getEffectiveSnapshotMode() {
        // Kill-switch check
        if (!ariaEnabled) {
            log.debug("Aria snapshot disabled by kill-switch, using legacy mode");
            return "legacy";
        }

        // Auto mode resolution
        if ("auto".equalsIgnoreCase(snapshotMode)) {
            if ("playwright".equalsIgnoreCase(engine)) {
                return "aria";
            }
            return "legacy";
        }

        // Validate: aria mode requires playwright
        if ("aria".equalsIgnoreCase(snapshotMode) && !"playwright".equalsIgnoreCase(engine)) {
            log.warn("Aria snapshot mode requested but Puppeteer engine is active. " +
                    "Aria snapshots require Playwright engine. Falling back to legacy.");
            return "legacy";
        }

        return snapshotMode;
    }

    /**
     * Checks if the current configuration uses Playwright engine.
     */
    public boolean isPlaywrightEngine() {
        return "playwright".equalsIgnoreCase(engine);
    }

    /**
     * Checks if aria snapshots are effectively enabled.
     */
    @Override
    public boolean isAriaEffectivelyEnabled() {
        return "aria".equalsIgnoreCase(getEffectiveSnapshotMode());
    }

    @Override
    public String toString() {
        return String.format(
                "BrowserConfiguration{engine='%s', snapshotMode='%s', ariaEnabled=%s, fallbackEnabled=%s, effectiveMode='%s'}",
                engine, snapshotMode, ariaEnabled, fallbackEnabled, getEffectiveSnapshotMode()
        );
    }
}
