package com.ai2qa.application.plan;

/**
 * Exception thrown when a saved plan cannot be found.
 * Mapped to HTTP 404 Not Found by the API layer.
 */
public class PlanNotFoundException extends RuntimeException {

    private final String planId;

    public PlanNotFoundException(String planId) {
        super("Saved plan not found: " + planId);
        this.planId = planId;
    }

    public String getPlanId() {
        return planId;
    }
}
