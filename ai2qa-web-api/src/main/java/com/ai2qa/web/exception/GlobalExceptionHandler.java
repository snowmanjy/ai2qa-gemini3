package com.ai2qa.web.exception;

import com.ai2qa.application.exception.ConcurrentLimitExceededException;
import com.ai2qa.application.exception.ProhibitedTargetException;
import com.ai2qa.application.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler for the API.
 *
 * <p>
 * Maps domain exceptions to appropriate HTTP responses using RFC 7807 Problem
 * Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles RateLimitExceededException.
     * Maps to HTTP 429 Too Many Requests.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage());

        problem.setTitle("Rate Limit Exceeded");
        problem.setType(URI.create("https://api.ai2qa.com/problems/rate-limit-exceeded"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles ConcurrentLimitExceededException.
     * Maps to HTTP 429 Too Many Requests.
     */
    @ExceptionHandler(ConcurrentLimitExceededException.class)
    public ProblemDetail handleConcurrentLimitExceeded(ConcurrentLimitExceededException ex) {
        log.warn("Concurrent limit exceeded: type={}, current={}, max={}",
                ex.getLimitType(), ex.getCurrentCount(), ex.getMaxAllowed());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage());

        problem.setTitle("Concurrent Test Limit Exceeded");
        problem.setType(URI.create("https://api.ai2qa.com/problems/concurrent-limit-exceeded"));
        problem.setProperty("limitType", ex.getLimitType().name());
        problem.setProperty("currentCount", ex.getCurrentCount());
        problem.setProperty("maxAllowed", ex.getMaxAllowed());
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles ProhibitedTargetException.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(ProhibitedTargetException.class)
    public ProblemDetail handleProhibitedTarget(ProhibitedTargetException ex) {
        log.warn("Prohibited target domain: {}", ex.getBlockedHost());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Testing this domain is prohibited by safety policy.");

        problem.setTitle("Prohibited Target");
        problem.setType(URI.create("https://api.ai2qa.com/problems/prohibited-target"));
        problem.setProperty("blockedHost", ex.getBlockedHost());
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles EntityNotFoundException.
     * Maps to HTTP 404 Not Found.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        log.debug("Entity not found: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage());

        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("https://api.ai2qa.com/problems/not-found"));
        problem.setProperty("entityType", ex.getEntityType());
        problem.setProperty("entityId", ex.getEntityId());
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles validation errors from @Valid annotations.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        log.debug("Validation failed: {}", ex.getMessage());

        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed: " + errors);

        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://api.ai2qa.com/problems/validation-error"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles IllegalArgumentException.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage());

        problem.setTitle("Invalid Request");
        problem.setType(URI.create("https://api.ai2qa.com/problems/invalid-request"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles SecurityException for tenant isolation violations.
     * Maps to HTTP 403 Forbidden.
     */
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleSecurityException(SecurityException ex) {
        log.error("Security violation: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Access denied");

        problem.setTitle("Forbidden");
        problem.setType(URI.create("https://api.ai2qa.com/problems/forbidden"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles file upload size exceeded.
     * Maps to HTTP 413 Payload Too Large.
     */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("File upload too large: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File size exceeds the maximum allowed limit (2MB)");

        problem.setTitle("Payload Too Large");
        problem.setType(URI.create("https://api.ai2qa.com/problems/payload-too-large"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles IO exceptions during file processing.
     * Maps to HTTP 400 Bad Request.
     */
    @ExceptionHandler(java.io.IOException.class)
    public ProblemDetail handleIOException(java.io.IOException ex) {
        log.warn("File processing failed: {}", ex.getMessage());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to process uploaded file: " + ex.getMessage());

        problem.setTitle("File Processing Error");
        problem.setType(URI.create("https://api.ai2qa.com/problems/file-processing-error"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Handles NoResourceFoundException for missing static resources.
     * This catches security scanner probes for /actuator/*, /.env, /wp-admin, etc.
     * Returns 404 without logging as error (these are expected bot probes).
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        // Log at debug level only - these are common security scanner probes
        log.debug("Resource not found (likely bot probe): {}", ex.getResourcePath());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Resource not found");

        problem.setTitle("Not Found");
        problem.setType(URI.create("https://api.ai2qa.com/problems/not-found"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }

    /**
     * Fallback handler for unexpected exceptions.
     * Maps to HTTP 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");

        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://api.ai2qa.com/problems/internal-error"));
        problem.setProperty("timestamp", Instant.now());

        return problem;
    }
}
