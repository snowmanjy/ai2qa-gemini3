package com.ai2qa.domain.context;

/**
 * Admin context.
 *
 * <p>Simplified - no admin functionality needed.
 * Always returns false for isAdmin().
 */
public final class AdminContext {

    private AdminContext() {
        // utility class
    }

    public static void setAdmin(boolean admin) {
        // No-op
    }

    public static boolean isAdmin() {
        // No admin mode
        return false;
    }

    public static void clear() {
        // No-op
    }
}
