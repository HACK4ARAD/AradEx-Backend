package aradexperience.dto.google;

import java.util.List;

/**
 * `GET /places/{id}/google`, `GET /businesses/{id}/google` (T17): ratingul, numărul de păreri și până la 5 recenzii
 * din Google Places API (New), prin backend, cu cache 24 h. Se afișează cu `attribution` („Google Maps”, cum cere
 * politica Google când nu e hartă Google) și cu linkul `mapsUrl` spre locul de pe Google Maps. Recenziile sunt ordonate
 * după stele (aplicația scrie asta lângă ele, cum cere politica).
 */
public record GoogleInfoDto(
        Double rating,
        Integer userRatingCount,
        List<GoogleReviewDto> reviews,
        String mapsUrl,
        String attribution) {

    public static final String ATTRIBUTION = "Google Maps";
}
