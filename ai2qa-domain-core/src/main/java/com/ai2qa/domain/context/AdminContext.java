package com.ai2qa.domain.context;

/**
 * Admin context for hackathon demo.
 *
 * <p>Simplified for hackathon - no admin functionality needed.
 * Always returns false for isAdmin().
 */
public final class AdminContext {

    private AdminContext() {
        // utility class
    }

    public static void setAdmin(boolean admin) {
        // No-op for hackathon
    }

    public static boolean isAdmin() {
        // No admin for hackathon demo
        return false;
    }

    public static void clear() {
        // No-op for hackathon
    }
}
