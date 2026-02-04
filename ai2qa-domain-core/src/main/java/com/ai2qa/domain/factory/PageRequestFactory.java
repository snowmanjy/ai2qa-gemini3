package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.PageRequest;

import java.util.Optional;

/**
 * Factory for PageRequest creation with normalization.
 */
public final class PageRequestFactory {

    private PageRequestFactory() {
    }

    public static Optional<PageRequest> create(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? PageRequest.DEFAULT_SIZE : Math.min(size, PageRequest.MAX_SIZE);
        return Optional.of(new PageRequest(safePage, safeSize));
    }

    public static PageRequest first(int size) {
        return create(0, size).orElse(defaultRequest());
    }

    public static PageRequest defaultRequest() {
        return new PageRequest(0, PageRequest.DEFAULT_SIZE);
    }
}
