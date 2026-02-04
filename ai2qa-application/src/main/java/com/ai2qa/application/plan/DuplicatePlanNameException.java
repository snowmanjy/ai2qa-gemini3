package com.ai2qa.application.plan;

/**
 * Exception thrown when attempting to create a saved plan with a name
 * that already exists for the tenant.
 * Mapped to HTTP 409 Conflict by the API layer.
 */
public class DuplicatePlanNameException extends RuntimeException {

    private final String planName;

    public DuplicatePlanNameException(String planName) {
        super("A saved plan with name '" + planName + "' already exists");
        this.planName = planName;
    }

    public String getPlanName() {
        return planName;
    }
}
