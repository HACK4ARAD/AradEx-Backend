package aradexperience.dto.google;

/**
 * O recenzie Google, așa cum o dă Places API (New). `authorUrl` / `authorPhotoUrl` = profilul autorului pe Google
 * (atribuirea cerută de Google: numele autorului, cu link când există); `relativeTime` e textul Google („acum o lună”).
 */
public record GoogleReviewDto(
        String author,
        String authorUrl,
        String authorPhotoUrl,
        int rating,
        String text,
        String relativeTime) {
}
