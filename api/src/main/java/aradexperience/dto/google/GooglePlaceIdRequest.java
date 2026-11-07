package aradexperience.dto.google;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Leagă (sau dezleagă, cu gol / null) un obiectiv sau o firmă de locul ei de pe Google. Place ID-ul Google are
 * doar litere, cifre, `_` și `-` (ex. `ChIJN1t_tDeuEmsRUsoyG83frY4`); îl găsești pe
 * https://developers.google.com/maps/documentation/places/web-service/place-id.
 */
public record GooglePlaceIdRequest(
        @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "must be a Google Place ID") String googlePlaceId) {
}
