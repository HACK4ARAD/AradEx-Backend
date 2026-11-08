package aradexperience.mapper.google;

import aradexperience.dto.google.GoogleInfoDto;
import aradexperience.dto.google.GoogleReviewDto;
import aradexperience.service.google.GooglePlaces;
import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Răspunsul Google Places (New) → DTO-urile din API.md §Google. */
@UtilityClass
public class GoogleMapper {

    public static final int MAX_REVIEWS = 5;

    /**
     * Recenziile sunt ordonate după stele, cele mai bune primele (cerința din 8 nov); la același număr de stele rămâne
     * ordinea lui Google („cele mai relevante”), pentru că sortarea e stabilă. Google dă prin API doar 5 recenzii, deci
     * se reordonează acestea, nu se aleg altele.
     */
    public static GoogleInfoDto toInfo(GooglePlaces.Place p) {
        List<GoogleReviewDto> reviews = p.reviews() == null ? List.of() : p.reviews().stream()
                .filter(Objects::nonNull)
                .map(GoogleMapper::toReview)
                .filter(r -> r.text() != null && !r.text().isBlank())
                .sorted(Comparator.comparingInt(GoogleReviewDto::rating).reversed())
                .limit(MAX_REVIEWS)
                .toList();
        String maps = p.googleMapsUri() != null ? p.googleMapsUri()
                : "https://www.google.com/maps/place/?q=place_id:" + p.id();
        return new GoogleInfoDto(p.rating(), p.userRatingCount(), reviews, maps, GoogleInfoDto.ATTRIBUTION);
    }

    /**
     * Textul tradus de Google în limba cerută; dacă lipsește, textul original. Dacă Google l-a tradus (limba textului ≠
     * limba originalului), `translated = true` și `originalText` = originalul — politica Google cere să-i spunem
     * utilizatorului că recenzia e tradusă.
     */
    public static GoogleReviewDto toReview(GooglePlaces.Review r) {
        GooglePlaces.AuthorAttribution a = r.authorAttribution();
        String shown = r.text() != null && r.text().text() != null ? r.text().text()
                : r.originalText() != null ? r.originalText().text() : null;
        boolean translated = r.text() != null && r.text().text() != null && r.originalText() != null
                && r.originalText().text() != null && r.text().languageCode() != null && r.originalText().languageCode() != null
                && !r.text().languageCode().equalsIgnoreCase(r.originalText().languageCode());
        return new GoogleReviewDto(
                a != null && a.displayName() != null ? a.displayName() : "Google",
                a != null ? a.uri() : null,
                a != null ? a.photoUri() : null,
                r.rating() != null ? r.rating() : 0,
                shown == null ? null : shown.trim(),
                r.relativePublishTimeDescription(),
                r.googleMapsUri(),
                translated ? Boolean.TRUE : null,
                translated ? r.originalText().text().trim() : null);
    }
}
