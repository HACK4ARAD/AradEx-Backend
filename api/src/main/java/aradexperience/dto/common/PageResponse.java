package aradexperience.dto.common;

import java.util.List;

/** Răspuns paginat conform docs/API.md: { content, page, size, totalElements }. Construit în `app` cu `util/Pages`. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
}
