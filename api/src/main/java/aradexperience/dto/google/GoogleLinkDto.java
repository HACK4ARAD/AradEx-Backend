package aradexperience.dto.google;

/**
 * Legătura unui obiectiv / a unei firme cu Google, pentru panouri (Primăria, firma): `googlePlaceId` (gol = nelegat),
 * `configured` = backend-ul are cheia Google, `info` = ratingul și recenziile (lipsește dacă nu e legat, nu e cheie sau
 * e o problemă), `problem` = de ce lipsește `info` deși e legat: `INVALID_ID` (Google nu cunoaște ID-ul) sau
 * `UNAVAILABLE` (Google nu răspunde acum).
 */
public record GoogleLinkDto(String googlePlaceId, boolean configured, GoogleInfoDto info, String problem) {

    public static final String INVALID_ID = "INVALID_ID";
    public static final String UNAVAILABLE = "UNAVAILABLE";

    public GoogleLinkDto(String googlePlaceId, boolean configured, GoogleInfoDto info) {
        this(googlePlaceId, configured, info, null);
    }
}
