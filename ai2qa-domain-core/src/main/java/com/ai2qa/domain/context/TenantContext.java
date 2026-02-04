package com.ai2qa.domain.context;

/**
 * Tenant context for the hackathon demo.
 *
 * <p>Simplified for single-tenant hackathon deployment.
 * Always returns the demo tenant ID.
 */
public final class TenantContext {

    private static final String HACKATHON_DEMO_TENANT = "hackathon-demo";

    private TenantContext() {
        // utility class
    }

    public static void setTenantId(String tenantId) {
        // No-op for hackathon - single tenant
    }

    public static String getTenantId() {
        // Always return the demo tenant
        return HACKATHON_DEMO_TENANT;
    }

    public static void clear() {
        // No-op for hackathon - single tenant
    }
}
