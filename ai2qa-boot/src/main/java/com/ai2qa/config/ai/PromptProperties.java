package com.ai2qa.config.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI prompt management.
 *
 * <p>Supports hot-reloading of prompts from external files without application restart.
 * Prompts are loaded in priority order:
 * <ol>
 *   <li>External path (filesystem) - highest priority</li>
 *   <li>Classpath (JAR bundled) - fallback</li>
 *   <li>Hardcoded PromptTemplates - ultimate fallback</li>
 * </ol>
 */
@Configuration
@ConfigurationProperties(prefix = "ai2qa.prompts")
public class PromptProperties {

    /**
     * External filesystem path for prompt files.
     * When set, prompts from this path take precedence over classpath resources.
     * Supports hot-reload when hot-reload-enabled is true.
     *
     * <p>Example: /opt/ai2qa/prompts
     */
    private String externalPath = "";

    /**
     * Classpath base path for embedded prompt files.
     * Files are loaded from this path within the JAR/classpath.
     *
     * <p>Default: prompts (loads from classpath:prompts/)
     */
    private String classpathPath = "prompts";

    /**
     * Interval in seconds between hot-reload checks.
     * The service polls external files for changes at this interval.
     * Set to 0 to disable hot-reload.
     *
     * <p>Recommended: 10-60 for development, 300+ for production
     */
    private int refreshIntervalSeconds = 60;

    /**
     * Enable or disable hot-reload functionality.
     * When disabled, prompts are loaded once at startup and never refreshed.
     * Useful for production environments where stability is prioritized.
     */
    private boolean hotReloadEnabled = true;

    public String getExternalPath() {
        return externalPath;
    }

    public void setExternalPath(String externalPath) {
        this.externalPath = externalPath;
    }

    public String getClasspathPath() {
        return classpathPath;
    }

    public void setClasspathPath(String classpathPath) {
        this.classpathPath = classpathPath;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public boolean isHotReloadEnabled() {
        return hotReloadEnabled;
    }

    public void setHotReloadEnabled(boolean hotReloadEnabled) {
        this.hotReloadEnabled = hotReloadEnabled;
    }

    /**
     * Check if an external path is configured.
     */
    public boolean hasExternalPath() {
        return externalPath != null && !externalPath.isBlank();
    }
}
