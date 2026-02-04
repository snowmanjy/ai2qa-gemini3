package com.ai2qa.application.run.view;

import java.util.List;
import java.util.function.Function;

/**
 * Pagination view model for API responses.
 */
public record PagedResultView<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public PagedResultView {
        if (content == null) {
            content = List.of();
        }
    }

    public <U> PagedResultView<U> map(Function<T, U> mapper) {
        List<U> mapped = content.stream().map(mapper).toList();
        return new PagedResultView<>(mapped, page, size, totalElements, totalPages);
    }
}
