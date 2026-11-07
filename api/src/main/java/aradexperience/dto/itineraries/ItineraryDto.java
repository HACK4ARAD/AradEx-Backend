package aradexperience.dto.itineraries;

import aradexperience.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Un itinerariu (T13). Generat de POST /ai/itinerary (nesalvat: `id`, `shareCode`, `createdAt` lipsesc) și trimis înapoi
 * neschimbat la POST /itineraries ca să fie salvat. `ask` = cererea omului într-o propoziție (balonul din aplicație),
 * `offline` = true când programul vine din planificatorul de rezervă (Claude n-a răspuns).
 */
public record ItineraryDto(
        Long id,
        String shareCode,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 1000) String summary,
        @Size(max = 300) String ask,
        @NotNull LocalDate date,
        @Min(1) @Max(2) int days,
        @NotNull TransportMode transport,
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String startTime,
        @Min(0) @Max(2880) int totalMinutes,
        @DecimalMin("0") @DecimalMax("10000") double totalKm,
        boolean offline,
        @NotEmpty @Size(max = 30) List<@NotNull @Valid ItineraryStop> stops,
        OffsetDateTime createdAt) {
}
