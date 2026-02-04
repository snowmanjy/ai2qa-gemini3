package com.ai2qa.domain.factory;

import com.ai2qa.domain.model.PageRequest;
import com.ai2qa.domain.model.PagedResult;

import java.util.List;

/**
 * Factory for PagedResult creation with safe defaults.
 */
public final class PagedResultFactory {

    private PagedResultFactory() {
    }

    public static <T> PagedResult<T> of(List<T> content, PageRequest request, long totalElements) {
        List<T> safeContent = content == null ? List.of() : List.copyOf(content);
        int size = request.size();
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PagedResult<>(safeContent, request.page(), size, totalElements, totalPages);
    }

    public static <T> PagedResult<T> empty(PageRequest request) {
        return new PagedResult<>(List.of(), request.page(), request.size(), 0, 0);
    }
}
