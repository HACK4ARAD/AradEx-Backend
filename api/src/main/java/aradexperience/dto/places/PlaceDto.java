package aradexperience.dto.places;

import aradexperience.enums.PlaceType;

import java.util.List;

/** Detaliul unui obiectiv = ghidul virtual (E3/W8): câmpurile din PlaceSummary + descriere, program, parcare, punctele de ghid ordonate. */
public record PlaceDto(
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
        boolean exteriorOnly,
        String slug,
        String description,
        String address,
        String openingHours,
        Double rating,
        String parkingHint,
        String imageCredit,
        List<GuidePointDto> guidePoints) {
}
