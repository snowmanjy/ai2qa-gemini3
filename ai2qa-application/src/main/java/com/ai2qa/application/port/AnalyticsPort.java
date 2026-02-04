package com.ai2qa.application.port;

import java.util.Map;

/**
 * Port for product analytics tracking.
 *
 * <p>Implementations may send events to PostHog, Mixpanel, or other analytics services.
 * Events are fire-and-forget - failures should be logged but not propagate to callers.
 */
public interface AnalyticsPort {

    /**
     * Tracks an event for a specific user/tenant.
     *
     * @param distinctId Unique identifier for the user (typically tenant ID)
     * @param event      Event name (e.g., "test_run_completed")
     * @param properties Event properties as key-value pairs
     */
    void capture(String distinctId, String event, Map<String, Object> properties);

    /**
     * Tracks an event with no additional properties.
     *
     * @param distinctId Unique identifier for the user
     * @param event      Event name
     */
    default void capture(String distinctId, String event) {
        capture(distinctId, event, Map.of());
    }

    /**
     * Identifies a user with properties for user profiles.
     *
     * @param distinctId Unique identifier for the user
     * @param properties User properties (e.g., plan, signup date)
     */
    void identify(String distinctId, Map<String, Object> properties);

    /**
     * Checks if analytics tracking is enabled.
     *
     * @return true if events will be sent, false if disabled
     */
    boolean isEnabled();
}
