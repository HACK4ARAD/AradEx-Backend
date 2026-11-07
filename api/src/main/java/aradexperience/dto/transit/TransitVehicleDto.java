package aradexperience.dto.transit;

import aradexperience.enums.TransitMode;

import java.time.OffsetDateTime;

/**
 * Un tramvai / autobuz pe hartă (T22). `routeShortName`, `mode`, `color` lipsesc dacă linia nu e în datele statice.
 * `stale` = poziția e mai veche de 120 s (`lastUpdated`); FE nu le arată ca live.
 */
public record TransitVehicleDto(
        String id,
        String label,
        int routeId,
        String routeShortName,
        TransitMode mode,
        String color,
        String headsign,
        double lat,
        double lng,
        Double heading,
        Integer delaySeconds,
        Integer nextStopId,
        OffsetDateTime nextStopArrival,
        OffsetDateTime lastUpdated,
        boolean stale) {
}
