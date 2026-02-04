package com.ai2qa.domain.model;

/**
 * Domain pagination request parameters.
 * Framework-agnostic alternative to Spring's Pageable.
 */
public record PageRequest(
        int page,
        int size
) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public long offset() {
        return (long) page * size;
    }
}
