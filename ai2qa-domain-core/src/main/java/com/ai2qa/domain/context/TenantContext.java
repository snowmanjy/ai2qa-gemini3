package com.ai2qa.domain.context;

/**
 * Tenant context for single-tenant deployment.
 *
 * <p>Simplified for single-tenant deployment.
 */
public final class TenantContext {

    private static final String HACKATHON_DEMO_TENANT = "hackathon-demo";

    private TenantContext() {
        // utility class
    }

    public static void setTenantId(String tenantId) {
        // No-op - single tenant
    }

    public static String getTenantId() {
        // Always return the default tenant
        return HACKATHON_DEMO_TENANT;
    }

    public static void clear() {
        // No-op - single tenant
    }
}
