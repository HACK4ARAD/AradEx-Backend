package aradexperience.util;

import aradexperience.dto.common.PageResponse;
import org.springframework.data.domain.Page;

import java.util.function.Function;

/** Spring Data Page → PageResponse (DTO-ul din `api`). */
public final class Pages {

    private Pages() {}

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }
}
