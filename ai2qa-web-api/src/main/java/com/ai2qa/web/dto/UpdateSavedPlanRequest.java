package com.ai2qa.web.dto;

import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

/**
 * Request DTO for updating an existing saved test plan.
 *
 * <p>All fields are optional - only non-null fields will be updated.
 *
 * @param name        New name for the plan (null to keep existing)
 * @param description New description (null to keep existing)
 * @param targetUrl   New target URL (null to keep existing)
 * @param goals       New goals (null to keep existing)
 * @param persona     New persona (null to keep existing)
 */
public record UpdateSavedPlanRequest(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Size(max = 2048, message = "Target URL must not exceed 2048 characters")
        @URL(message = "Target URL must be valid")
        String targetUrl,

        @Size(max = 10, message = "Max 10 goals allowed per plan")
        List<@Size(max = 500, message = "Goal description too long") String> goals,

        String persona) {
}
