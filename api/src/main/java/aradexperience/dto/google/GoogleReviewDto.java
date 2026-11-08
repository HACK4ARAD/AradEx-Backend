package aradexperience.dto.google;

/**
 * O recenzie Google, așa cum o dă Places API (New), cu ce cere politica Google la afișare: autorul (`authorUrl` /
 * `authorPhotoUrl` = profilul lui), `mapsUrl` = recenzia pe Google Maps, `translated` = `true` dacă Google a tradus-o
 * în limba cerută (se spune utilizatorului), iar atunci `originalText` = textul original. `relativeTime` e textul
 * Google („acum o lună”).
 */
public record GoogleReviewDto(
        String author,
        String authorUrl,
        String authorPhotoUrl,
        int rating,
        String text,
        String relativeTime,
        String mapsUrl,
        Boolean translated,
        String originalText) {

    public GoogleReviewDto(String author, String authorUrl, String authorPhotoUrl, int rating, String text, String relativeTime) {
        this(author, authorUrl, authorPhotoUrl, rating, text, relativeTime, null, null, null);
    }
}
