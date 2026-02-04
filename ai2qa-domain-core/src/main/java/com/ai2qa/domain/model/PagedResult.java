package com.ai2qa.domain.model;

import java.util.List;
import java.util.function.Function;

/**
 * Domain pagination result.
 * Framework-agnostic alternative to Spring's Page.
 *
 * @param <T> The type of elements in this page
 */
public record PagedResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public boolean hasContent() {
        return !content.isEmpty();
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean isFirst() {
        return page == 0;
    }

    public boolean isLast() {
        return !hasNext();
    }

    public <U> PagedResult<U> map(Function<T, U> mapper) {
        List<U> mapped = content.stream().map(mapper).toList();
        return new PagedResult<>(mapped, page, size, totalElements, totalPages);
    }
}
