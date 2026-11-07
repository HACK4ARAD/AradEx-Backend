package aradexperience.dto.google;

/**
 * Legătura unui obiectiv / a unei firme cu Google, pentru panouri (Primăria, firma): `googlePlaceId` (gol = nelegat),
 * `configured` = backend-ul are cheia Google, `info` = ratingul și recenziile (lipsește dacă nu e legat, nu e cheie sau
 * Google nu răspunde acum).
 */
public record GoogleLinkDto(String googlePlaceId, boolean configured, GoogleInfoDto info) {
}
