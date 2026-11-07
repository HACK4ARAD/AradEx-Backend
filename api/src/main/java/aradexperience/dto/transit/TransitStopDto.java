package aradexperience.dto.transit;

import java.util.List;

/** O stație CTP (T22). `id` = id-ul Telelink; `routeIds` = liniile care opresc aici (din variantele statice). */
public record TransitStopDto(
        int id,
        String name,
        String code,
        double lat,
        double lng,
        List<Integer> routeIds) {
}
