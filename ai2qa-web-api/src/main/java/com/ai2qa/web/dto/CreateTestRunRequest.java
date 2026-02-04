package com.ai2qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * Request DTO for creating a new test run.
 *
 * @param targetUrl         The URL to test
 * @param goals             List of test goals to achieve
 * @param persona           Optional test persona (defaults to STANDARD if blank)
 * @param cookiesJson       Optional JSON string of cookies for authenticated testing
 * @param recaptchaToken    Optional reCAPTCHA v3 token for abuse prevention
 */
public record CreateTestRunRequest(
        @NotBlank(message = "Target URL is required")
        @Size(max = 2048, message = "Target URL must not exceed 2048 characters")
        @URL(message = "Target URL must be valid")
        String targetUrl,

        @Size(max = 10, message = "Max 10 goals allowed per run")
        List<@Size(max = 500, message = "Goal description too long") @NotBlank String> goals,

        String persona,

        @Size(max = 50000, message = "Cookies JSON too large")
        String cookiesJson,

        String recaptchaToken
) {

    /**
     * Default persona name. Mirrors {@code TestPersona.DEFAULT_NAME} from the domain layer.
     * Web DTOs cannot depend on domain types (architecture rule), so the constant is duplicated here.
     */
    public static final String DEFAULT_PERSONA = "STANDARD";

    public CreateTestRunRequest {
        if (goals == null) {
            goals = List.of();
        }
        if (persona == null || persona.isBlank()) {
            persona = DEFAULT_PERSONA;
        }
    }
}
