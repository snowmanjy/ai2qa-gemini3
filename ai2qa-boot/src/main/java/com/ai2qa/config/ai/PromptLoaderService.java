package com.ai2qa.config.ai;

import com.ai2qa.application.ai.PromptTemplates;
import com.ai2qa.application.port.PromptLoaderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading AI prompts from external files with hot-reload support.
 *
 * <p>Loading priority:
 * <ol>
 *   <li>External filesystem path (if configured)</li>
 *   <li>Classpath resources</li>
 *   <li>Hardcoded PromptTemplates (fallback)</li>
 * </ol>
 *
 * <p>Hot-reload: When external path is configured, files are checked for changes
 * at the configured interval and reloaded automatically.
 */
@Component
public class PromptLoaderService implements PromptLoaderPort {

    private static final Logger log = LoggerFactory.getLogger(PromptLoaderService.class);

    private final PromptProperties properties;
    private final ResourceLoader resourceLoader;

    // Cache: Map<"role/promptType" or "role/promptType-mode", promptContent>
    private final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

    // Track file modification times for hot-reload
    private final ConcurrentHashMap<String, Long> fileModifiedTimes = new ConcurrentHashMap<>();

    // Timestamp of last reload
    private volatile Instant lastReloadTime = Instant.now();

    // Fallback prompts from embedded PromptTemplates (loaded once at startup)
    private final Map<String, String> fallbackPrompts;

    public PromptLoaderService(PromptProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.fallbackPrompts = initializeFallbacks();
        loadAllPrompts();
        log.info("PromptLoaderService initialized. External path: '{}', Hot-reload: {}, Interval: {}s",
                properties.getExternalPath(),
                properties.isHotReloadEnabled(),
                properties.getRefreshIntervalSeconds());
    }

    @Override
    public String getPrompt(String role, String promptType, String mode) {
        String cacheKey = buildCacheKey(role, promptType, mode);

        return promptCache.computeIfAbsent(cacheKey, key -> {
            Optional<String> loaded = loadPromptFromSources(role, promptType, mode);
            return loaded.orElseGet(() -> getFallback(cacheKey));
        });
    }

    @Override
    public void reload() {
        log.info("Manual prompt reload triggered");
        promptCache.clear();
        fileModifiedTimes.clear();
        loadAllPrompts();
        lastReloadTime = Instant.now();
        log.info("Prompt reload complete. {} prompts cached.", promptCache.size());
    }

    @Override
    public Instant getLastReloadTime() {
        return lastReloadTime;
    }

    /**
     * Scheduled task for hot-reload.
     * Checks external files for modifications and reloads if changed.
     */
    @Scheduled(fixedDelayString = "${ai2qa.prompts.refresh-interval-seconds:60}000")
    public void checkForUpdates() {
        if (!properties.isHotReloadEnabled()) {
            return;
        }

        if (!properties.hasExternalPath()) {
            return; // No external path configured, skip hot-reload
        }

        log.debug("Checking for prompt file updates...");
        boolean anyUpdated = false;

        for (String cacheKey : promptCache.keySet()) {
            Path filePath = cacheKeyToFilePath(cacheKey);
            if (filePath != null && Files.exists(filePath)) {
                try {
                    long currentModified = Files.getLastModifiedTime(filePath).toMillis();
                    Long previousModified = fileModifiedTimes.get(cacheKey);

                    if (previousModified == null || currentModified > previousModified) {
                        String newContent = Files.readString(filePath, StandardCharsets.UTF_8);
                        promptCache.put(cacheKey, newContent);
                        fileModifiedTimes.put(cacheKey, currentModified);
                        log.info("Hot-reloaded prompt: {}", cacheKey);
                        anyUpdated = true;
                    }
                } catch (IOException e) {
                    log.warn("Failed to check/reload prompt: {}", cacheKey, e);
                }
            }
        }

        if (anyUpdated) {
            lastReloadTime = Instant.now();
        }
    }

    /**
     * Load all known prompts at startup.
     */
    private void loadAllPrompts() {
        // Pre-load all known prompt combinations
        String[][] promptKeys = {
                {"architect", "system", null},
                {"architect", "json-schema", null},
                {"hunter", "system", "aria"},
                {"hunter", "system", "legacy"},
                {"healer", "system", "aria"},
                {"healer", "system", "legacy"},
                {"healer", "json-schema", "aria"},
                {"healer", "json-schema", "legacy"},
                {"reporter", "system", null},
                {"reporter", "success-schema", null},
                {"reporter", "failure-schema", null}
        };

        for (String[] key : promptKeys) {
            String role = key[0];
            String promptType = key[1];
            String mode = key[2];
            getPrompt(role, promptType, mode); // This populates the cache
        }
    }

    /**
     * Try to load prompt from sources in priority order.
     */
    private Optional<String> loadPromptFromSources(String role, String promptType, String mode) {
        // 1. Try external filesystem path first (highest priority)
        if (properties.hasExternalPath()) {
            Optional<String> external = loadFromFilesystem(role, promptType, mode);
            if (external.isPresent()) {
                log.debug("Loaded prompt from external path: {}/{}-{}", role, promptType, mode);
                return external;
            }
        }

        // 2. Try classpath resources
        Optional<String> classpath = loadFromClasspath(role, promptType, mode);
        if (classpath.isPresent()) {
            log.debug("Loaded prompt from classpath: {}/{}-{}", role, promptType, mode);
            return classpath;
        }

        return Optional.empty();
    }

    /**
     * Load prompt from external filesystem.
     */
    private Optional<String> loadFromFilesystem(String role, String promptType, String mode) {
        Path path = buildFilePath(properties.getExternalPath(), role, promptType, mode);
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                // Track modification time for hot-reload
                String cacheKey = buildCacheKey(role, promptType, mode);
                fileModifiedTimes.put(cacheKey, Files.getLastModifiedTime(path).toMillis());
                return Optional.of(content);
            } catch (IOException e) {
                log.warn("Failed to read external prompt file: {}", path, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Load prompt from classpath resources.
     */
    private Optional<String> loadFromClasspath(String role, String promptType, String mode) {
        String resourcePath = buildResourcePath(role, promptType, mode);
        Resource resource = resourceLoader.getResource("classpath:" + resourcePath);

        if (resource.exists()) {
            try (InputStream is = resource.getInputStream()) {
                return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.warn("Failed to read classpath prompt: {}", resourcePath, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Initialize fallback prompts from hardcoded PromptTemplates.
     */
    private Map<String, String> initializeFallbacks() {
        Map<String, String> fallbacks = new HashMap<>();
        fallbacks.put("architect/system", PromptTemplates.ARCHITECT_SYSTEM_PROMPT);
        fallbacks.put("architect/json-schema", PromptTemplates.ARCHITECT_JSON_SCHEMA);
        fallbacks.put("hunter/system-aria", PromptTemplates.HUNTER_SYSTEM_PROMPT_ARIA);
        fallbacks.put("hunter/system-legacy", PromptTemplates.HUNTER_SYSTEM_PROMPT_LEGACY);
        fallbacks.put("healer/system-aria", PromptTemplates.HEALER_SYSTEM_PROMPT_ARIA);
        fallbacks.put("healer/system-legacy", PromptTemplates.HEALER_SYSTEM_PROMPT_LEGACY);
        fallbacks.put("healer/json-schema-aria", PromptTemplates.HEALER_JSON_SCHEMA_ARIA);
        fallbacks.put("healer/json-schema-legacy", PromptTemplates.HEALER_JSON_SCHEMA_LEGACY);
        fallbacks.put("reporter/system", PromptTemplates.REPORTER_SYSTEM_PROMPT);
        fallbacks.put("reporter/success-schema", PromptTemplates.REPORTER_SUCCESS_SCHEMA);
        fallbacks.put("reporter/failure-schema", PromptTemplates.REPORTER_FAILURE_SCHEMA);
        return Map.copyOf(fallbacks);
    }

    /**
     * Get fallback prompt from embedded PromptTemplates.
     */
    private String getFallback(String cacheKey) {
        String fallback = fallbackPrompts.get(cacheKey);
        if (fallback != null) {
            log.debug("Using fallback prompt for: {}", cacheKey);
            return fallback;
        }
        log.warn("No fallback found for prompt: {}", cacheKey);
        return "";
    }

    /**
     * Build cache key from role, type, and mode.
     */
    private String buildCacheKey(String role, String promptType, String mode) {
        if (mode == null || mode.isBlank()) {
            return role + "/" + promptType;
        }
        return role + "/" + promptType + "-" + mode;
    }

    /**
     * Build filesystem path for a prompt file.
     */
    private Path buildFilePath(String basePath, String role, String promptType, String mode) {
        String filename = buildFilename(promptType, mode);
        return Path.of(basePath, role, filename);
    }

    /**
     * Build classpath resource path for a prompt file.
     */
    private String buildResourcePath(String role, String promptType, String mode) {
        String filename = buildFilename(promptType, mode);
        return properties.getClasspathPath() + "/" + role + "/" + filename;
    }

    /**
     * Build filename from type and mode.
     */
    private String buildFilename(String promptType, String mode) {
        if (mode == null || mode.isBlank()) {
            return promptType + ".md";
        }
        return promptType + "-" + mode + ".md";
    }

    /**
     * Convert cache key back to filesystem path for hot-reload checking.
     */
    private Path cacheKeyToFilePath(String cacheKey) {
        if (!properties.hasExternalPath()) {
            return null;
        }

        // Parse cache key: "role/promptType" or "role/promptType-mode"
        String[] parts = cacheKey.split("/");
        if (parts.length != 2) {
            return null;
        }

        String role = parts[0];
        String typeAndMode = parts[1];

        // Check if mode is present
        int dashIndex = typeAndMode.lastIndexOf('-');
        String promptType;
        String mode;

        // Handle special cases like "json-schema-aria"
        if (typeAndMode.startsWith("json-schema")) {
            if (typeAndMode.equals("json-schema")) {
                promptType = "json-schema";
                mode = null;
            } else {
                promptType = "json-schema";
                mode = typeAndMode.substring("json-schema-".length());
            }
        } else if (typeAndMode.startsWith("success-schema") || typeAndMode.startsWith("failure-schema")) {
            promptType = typeAndMode;
            mode = null;
        } else if (dashIndex > 0) {
            promptType = typeAndMode.substring(0, dashIndex);
            mode = typeAndMode.substring(dashIndex + 1);
        } else {
            promptType = typeAndMode;
            mode = null;
        }

        return buildFilePath(properties.getExternalPath(), role, promptType, mode);
    }
}
