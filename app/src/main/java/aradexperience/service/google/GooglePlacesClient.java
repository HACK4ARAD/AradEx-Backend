package aradexperience.service.google;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cererile spre Google Places API (New), cu cheia din `GOOGLE_PLACES_API_KEY` (în producție din Secrets Manager,
 * `aradex/google-places`; local din `.env`). Cheia pleacă doar în header-ul `X-Goog-Api-Key`, nu se loghează și nu
 * ajunge în aplicație. Câmpurile cerute sunt limitate prin `X-Goog-FieldMask` (costul depinde de ele):
 * căutarea cere doar `places.id` (SKU „Text Search Essentials – IDs Only”), detaliile cer rating + recenzii
 * („Place Details Enterprise + Atmosphere”; `displayName`, folosit la verificarea potrivirii, e dintr-un nivel mai mic, deci
 * nu costă în plus). Orice eroare iese ca `RestClientException`; cache-ul e în `GoogleService`.
 */
@Component
public class GooglePlacesClient {

    static final String DEFAULT_BASE = "https://places.googleapis.com/v1";
    static final String DETAILS_FIELDS = "id,displayName,rating,userRatingCount,reviews,googleMapsUri";
    static final String SEARCH_FIELDS = "places.id";
    /** Căutarea e limitată la un dreptunghi de ~±450 m în jurul coordonatelor noastre, ca să nu lege alt loc cu același nume. */
    static final double BOX_LAT = 0.004;
    static final double BOX_LNG = 0.006;

    private final RestClient http;
    private final String apiKey;

    @Autowired
    public GooglePlacesClient(@Value("${app.google.places-base-url:" + DEFAULT_BASE + "}") String baseUrl,
                              @Value("${app.google.places-api-key:${GOOGLE_PLACES_API_KEY:}}") String apiKey,
                              RestClient.Builder builder) {
        this(builder.clone().requestFactory(timeouts()).baseUrl(baseUrl).build(), apiKey);
    }

    /** Pentru teste: clientul vine legat de `MockRestServiceServer`. */
    GooglePlacesClient(RestClient http, String apiKey) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /** Fără cheie nu se face nicio cerere spre Google (endpoint-urile răspund 404, aplicația ascunde blocul). */
    public boolean configured() {
        return !apiKey.isEmpty();
    }

    /** Ratingul, numărul de păreri, cel mult 5 recenzii (cele „relevante” după Google) și linkul Google Maps. */
    public GooglePlaces.Place details(String placeId, String languageCode) {
        return http.get()
                .uri(b -> b.path("/places/{id}").queryParam("languageCode", languageCode).build(placeId))
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", DETAILS_FIELDS)
                .retrieve()
                .body(GooglePlaces.Place.class);
    }

    /** ID-ul primului loc găsit după text, doar în jurul coordonatelor date; gol dacă nu e niciunul acolo. */
    public Optional<String> findId(String query, double lat, double lng) {
        Map<String, Object> body = Map.of(
                "textQuery", query,
                "languageCode", "ro",
                "regionCode", "RO",
                "pageSize", 1,
                "locationRestriction", Map.of("rectangle", Map.of(
                        "low", Map.of("latitude", lat - BOX_LAT, "longitude", lng - BOX_LNG),
                        "high", Map.of("latitude", lat + BOX_LAT, "longitude", lng + BOX_LNG))));
        GooglePlaces.SearchResult res = http.post()
                .uri("/places:searchText")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", SEARCH_FIELDS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GooglePlaces.SearchResult.class);
        List<GooglePlaces.IdOnly> places = res == null || res.places() == null ? List.of() : res.places();
        return places.stream().map(GooglePlaces.IdOnly::id).filter(id -> id != null && !id.isBlank()).findFirst();
    }

    /** Google răspunde în sub o secundă; un ecran nu așteaptă mai mult de câteva secunde după o sursă căzută. */
    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3_000);
        f.setReadTimeout(6_000);
        return f;
    }
}
