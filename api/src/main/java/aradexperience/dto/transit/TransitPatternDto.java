package aradexperience.dto.transit;

import java.util.List;

/**
 * O variantă de traseu a unei linii (sens / buclă). `index` vine din Telelink (poate fi negativ, unic doar în linie);
 * `stopIds` în ordinea opririlor (o stație poate apărea de două ori într-o buclă); `path` = traseul pe stradă / șine,
 * puncte `[lat, lng]` (simplificat la ~5 m).
 */
public record TransitPatternDto(
        int index,
        Integer fromStopId,
        Integer toStopId,
        List<Integer> stopIds,
        List<double[]> path) {
}
