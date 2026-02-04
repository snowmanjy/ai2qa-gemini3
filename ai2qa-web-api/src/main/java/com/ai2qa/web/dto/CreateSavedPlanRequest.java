package com.ai2qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * Request DTO for creating a new saved test plan.
 *
 * @param name        User-defined name for the plan (unique per tenant)
 * @param description Optional description of what the plan tests
 * @param targetUrl   The URL to test
 * @param goals       List of test goals to achieve
 * @param persona     Optional test persona (defaults to STANDARD if blank)
 */
public record CreateSavedPlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @NotBlank(message = "Target URL is required")
        @Size(max = 2048, message = "Target URL must not exceed 2048 characters")
        @URL(message = "Target URL must be valid")
        String targetUrl,

        @Size(max = 10, message = "Max 10 goals allowed per plan")
        List<@Size(max = 500, message = "Goal description too long") @NotBlank String> goals,

        String persona) {

    /**
     * Default persona name. Mirrors {@code TestPersona.DEFAULT_NAME} from the domain layer.
     * Web DTOs cannot depend on domain types (architecture rule), so the constant is duplicated here.
     */
    public static final String DEFAULT_PERSONA = "STANDARD";

    public CreateSavedPlanRequest {
        if (goals == null) {
            goals = List.of();
        }
        if (persona == null || persona.isBlank()) {
            persona = DEFAULT_PERSONA;
        }
    }
}
