package com.ai2qa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a saved plan from a test run.
 *
 * @param name        User-defined name for the new plan (required, unique per tenant)
 * @param description Optional description of what the plan tests
 */
public record SaveTestRunAsPlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description) {
}
