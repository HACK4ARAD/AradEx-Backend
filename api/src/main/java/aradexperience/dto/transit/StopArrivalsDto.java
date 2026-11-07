package aradexperience.dto.transit;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * `GET /transit/stops/{id}/arrivals` și `GET /transit/nearest`: stația, sosirile următoare (cel mult 8, după oră) și
 * starea sursei. `distanceKm` doar la `nearest`. `live=false` = rezervă statică: fără sosiri (sau ultimele știute),
 * dar cu `stop.routeIds` = liniile care opresc aici.
 */
public record StopArrivalsDto(
        TransitStopDto stop,
        Double distanceKm,
        boolean live,
        OffsetDateTime updatedAt,
        String attribution,
        List<TransitArrivalDto> arrivals) {
}
