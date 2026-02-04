package com.ai2qa.application.exception;

/**
 * Thrown when a rate limit is exceeded.
 * This prevents abuse by limiting requests per user, IP, or target domain.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
