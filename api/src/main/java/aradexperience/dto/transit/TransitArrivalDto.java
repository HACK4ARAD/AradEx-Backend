package aradexperience.dto.transit;

import aradexperience.enums.TransitMode;

import java.time.OffsetDateTime;

/**
 * O sosire într-o stație. `at` = ora estimată (programat + întârziere), `minutes` = peste câte minute, la momentul
 * răspunsului (0 = „acum”); `realtime=false` = doar ora din program.
 */
public record TransitArrivalDto(
        int routeId,
        String routeShortName,
        TransitMode mode,
        String color,
        String headsign,
        int stopId,
        OffsetDateTime at,
        int minutes,
        int delaySeconds,
        boolean realtime) {
}
