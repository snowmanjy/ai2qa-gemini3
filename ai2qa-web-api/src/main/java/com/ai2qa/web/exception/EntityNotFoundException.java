package com.ai2qa.web.exception;

/**
 * Exception thrown when a requested entity is not found.
 * Mapped to HTTP 404 Not Found.
 */
public class EntityNotFoundException extends RuntimeException {

    private final String entityType;
    private final String entityId;

    public EntityNotFoundException(String entityType, String entityId) {
        super(String.format("%s not found: %s", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public static EntityNotFoundException testRun(String id) {
        return new EntityNotFoundException("TestRun", id);
    }

    public static EntityNotFoundException tenant(String id) {
        return new EntityNotFoundException("Tenant", id);
    }

    public static EntityNotFoundException savedPlan(String id) {
        return new EntityNotFoundException("SavedTestPlan", id);
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }
}
