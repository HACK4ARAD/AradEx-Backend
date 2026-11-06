package ro.aradexperience.api.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Răspuns paginat conform docs/API.md: { content, page, size, totalElements }. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }
}
