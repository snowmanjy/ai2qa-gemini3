package com.ai2qa.config.ai;

import com.ai2qa.application.port.PromptLoaderPort;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Actuator endpoint for managing AI prompts.
 *
 * <p>Provides read and write operations for prompt management:
 * <ul>
 *   <li>GET /actuator/prompts - Get status and last reload time</li>
 *   <li>POST /actuator/prompts - Trigger manual reload</li>
 * </ul>
 *
 * <p>The endpoint is available at /actuator/prompts when
 * management.endpoints.web.exposure.include contains "prompts".
 */
@Component
@Endpoint(id = "prompts")
public class PromptActuatorEndpoint {

    private final PromptLoaderPort promptLoader;
    private final PromptProperties properties;

    public PromptActuatorEndpoint(PromptLoaderPort promptLoader, PromptProperties properties) {
        this.promptLoader = promptLoader;
        this.properties = properties;
    }

    /**
     * GET /actuator/prompts
     *
     * @return Status information about prompt loading
     */
    @ReadOperation
    public Map<String, Object> status() {
        return Map.of(
                "lastReload", promptLoader.getLastReloadTime().toString(),
                "hotReloadEnabled", properties.isHotReloadEnabled(),
                "refreshIntervalSeconds", properties.getRefreshIntervalSeconds(),
                "externalPath", properties.getExternalPath() != null ? properties.getExternalPath() : "",
                "classpathPath", properties.getClasspathPath()
        );
    }

    /**
     * POST /actuator/prompts
     *
     * <p>Triggers an immediate reload of all prompts from source files.
     *
     * @return Result of the reload operation
     */
    @WriteOperation
    public Map<String, Object> reload() {
        Instant beforeReload = promptLoader.getLastReloadTime();
        promptLoader.reload();
        Instant afterReload = promptLoader.getLastReloadTime();

        return Map.of(
                "status", "reloaded",
                "previousReload", beforeReload.toString(),
                "currentReload", afterReload.toString()
        );
    }
}
