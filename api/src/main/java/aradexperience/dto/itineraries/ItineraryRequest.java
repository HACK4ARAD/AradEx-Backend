package aradexperience.dto.itineraries;

import aradexperience.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /ai/itinerary (T13): ziua, ora de start (`10:00` implicit), cât timp — `days = 2` (două zile de câte 8 ore) sau `hours`
 * (1–12, implicit 4) —, transportul, interesele (texte libere: „istorie”, „crame & vin”…), dorința în cuvintele omului,
 * punctul de plecare (implicit centrul) și limba (`ro` implicit).
 */
public record ItineraryRequest(
        @NotNull LocalDate date,
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String startTime,
        @Min(1) @Max(12) Integer hours,
        @Min(1) @Max(2) Integer days,
        @NotNull TransportMode transport,
        @Size(max = 10) List<@NotBlank @Size(max = 40) String> interests,
        @Size(max = 500) String freeText,
        @Valid GeoPoint start,
        @Pattern(regexp = "ro|en") String locale) {
}
