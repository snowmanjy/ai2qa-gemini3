package com.ai2qa.domain.model.knowledge;

/**
 * Visibility level for knowledge patterns.
 */
public enum Visibility {
    /**
     * Shared globally across all tenants.
     */
    GLOBAL,

    /**
     * Visible only to a specific tenant/organization.
     */
    TENANT,

    /**
     * Private to a specific user or test run.
     */
    PRIVATE
}
