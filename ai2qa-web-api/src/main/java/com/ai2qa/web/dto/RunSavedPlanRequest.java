package com.ai2qa.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for running a saved test plan.
 *
 * <p>The cookies are provided at run-time for security - they are NOT
 * stored in the saved plan itself.
 *
 * @param cookiesJson   Optional JSON string of cookies for authenticated testing
 * @param executionMode Optional execution mode (CLOUD or LOCAL_AGENT), defaults to CLOUD
 * @param agentId       Optional agent ID for LOCAL_AGENT mode
 */
public record RunSavedPlanRequest(
        @Size(max = 50000, message = "Cookies JSON too large")
        String cookiesJson,
        String executionMode,
        String agentId) {

    public RunSavedPlanRequest {
        // Normalize null to empty string for easier handling
    }
}
