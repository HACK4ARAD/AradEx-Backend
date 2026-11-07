package aradexperience.mapper.google;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleReviewDto;
import aradexperience.service.google.GooglePlaces;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Objects;

/** Răspunsul Google Places (New) → DTO-urile din API.md §Google. */
@UtilityClass
public class GoogleMapper {

    public static final int MAX_REVIEWS = 5;

    public static GoogleInfoDto toInfo(GooglePlaces.Place p) {
        List<GoogleReviewDto> reviews = p.reviews() == null ? List.of() : p.reviews().stream()
                .filter(Objects::nonNull)
                .map(GoogleMapper::toReview)
                .filter(r -> r.text() != null && !r.text().isBlank())
                .limit(MAX_REVIEWS)
                .toList();
        String maps = p.googleMapsUri() != null ? p.googleMapsUri()
                : "https://www.google.com/maps/place/?q=place_id:" + p.id();
        return new GoogleInfoDto(p.rating(), p.userRatingCount(), reviews, maps, GoogleInfoDto.ATTRIBUTION);
    }

    /** Textul tradus de Google în limba cerută; dacă lipsește, textul original al recenziei. */
    public static GoogleReviewDto toReview(GooglePlaces.Review r) {
        GooglePlaces.AuthorAttribution a = r.authorAttribution();
        String text = r.text() != null && r.text().text() != null ? r.text().text()
                : r.originalText() != null ? r.originalText().text() : null;
        return new GoogleReviewDto(
                a != null && a.displayName() != null ? a.displayName() : "Google",
                a != null ? a.uri() : null,
                a != null ? a.photoUri() : null,
                r.rating() != null ? r.rating() : 0,
                text == null ? null : text.trim(),
                r.relativePublishTimeDescription());
    }
}
