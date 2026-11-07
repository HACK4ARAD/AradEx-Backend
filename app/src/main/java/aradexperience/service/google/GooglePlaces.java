package aradexperience.service.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Formatul răspunsurilor Google Places API (New) folosite în T17; câmpurile în plus se ignoră. */
public final class GooglePlaces {

    private GooglePlaces() {}

    /** `GET /places/{id}` cu field mask `id,displayName,rating,userRatingCount,reviews,googleMapsUri`. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Place(String id, LocalizedText displayName, Double rating, Integer userRatingCount, String googleMapsUri, List<Review> reviews) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Review(Integer rating, LocalizedText text, LocalizedText originalText,
                         String relativePublishTimeDescription, AuthorAttribution authorAttribution) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocalizedText(String text, String languageCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorAttribution(String displayName, String uri, String photoUri) {}

    /** `POST /places:searchText` cu field mask `places.id` (doar ID-uri). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SearchResult(List<IdOnly> places) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdOnly(String id) {}
}
