package com.ai2qa.application.exception;

/**
 * Thrown when concurrent test limits are exceeded.
 * This prevents system overload by limiting simultaneous running tests.
 */
public class ConcurrentLimitExceededException extends RuntimeException {

    private final LimitType limitType;
    private final int currentCount;
    private final int maxAllowed;

    public ConcurrentLimitExceededException(LimitType limitType, int currentCount, int maxAllowed) {
        super(formatMessage(limitType, currentCount, maxAllowed));
        this.limitType = limitType;
        this.currentCount = currentCount;
        this.maxAllowed = maxAllowed;
    }

    private static String formatMessage(LimitType type, int current, int max) {
        return switch (type) {
            case USER -> String.format(
                    "User concurrent test limit exceeded. You have %d tests running (max: %d). " +
                    "Please wait for a test to complete before starting another.", current, max);
            case GLOBAL -> String.format(
                    "System is at capacity with %d concurrent tests (max: %d). " +
                    "Please try again in a few moments.", current, max);
        };
    }

    public LimitType getLimitType() {
        return limitType;
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public int getMaxAllowed() {
        return maxAllowed;
    }

    public enum LimitType {
        USER,
        GLOBAL
    }
}
