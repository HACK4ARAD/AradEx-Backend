package aradexperience.dto.places;

import aradexperience.enums.PlaceType;

import java.util.List;

/**
 * Obiectiv în liste (W7, „Locuri din Arad”, hartă). `distanceKm` doar când cererea are `near`.
 * `guidePointsCount` + `guideMinutes` = badge-ul „Ghid · N puncte · M min”, `exteriorOnly` = „doar exterior” (adăugate în T05, vezi docs/API.md).
 */
public record PlaceSummary(
        long id,
        String name,
        PlaceType type,
        String imageUrl,
        double lat,
        double lng,
        int visitMinutes,
        List<String> tags,
        Double distanceKm,
        int guidePointsCount,
        int guideMinutes,
        boolean exteriorOnly) {
}
