package aradexperience.dto.itineraries;

import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.TransportMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * O oprire din itinerariu (T13). `order` = 1..n pe tot itinerariul (peste zile), `day` = 1 sau 2. `refId` = id-ul locului,
 * evenimentului sau ofertei, după `kind`. `subtitle` = tipul locului, sala evenimentului sau firma ofertei. Orele sunt ISO
 * cu offset (Europe/Bucharest). Drumul de la oprirea anterioară: minute, km și mod (0 la prima oprire din zi).
 * `parkingHint` doar cu mașina; `hasGuide` = locul are ghid audio în aplicație.
 */
public record ItineraryStop(
        @Min(1) @Max(60) int order,
        @Min(1) @Max(2) int day,
        @NotNull ItineraryStopKind kind,
        @NotNull Long refId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String subtitle,
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lng,
        @NotNull OffsetDateTime arriveAt,
        @NotNull OffsetDateTime leaveAt,
        @Min(0) @Max(1440) int durationMin,
        @Min(0) @Max(1440) int travelMinutesFromPrev,
        @DecimalMin("0") @DecimalMax("1000") double travelKmFromPrev,
        @NotNull TransportMode travelMode,
        @Size(max = 500) String note,
        @Size(max = 500) String parkingHint,
        boolean hasGuide,
        @Size(max = 500) String imageUrl) {
}
