package aradexperience.service.transit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Răspunsurile API-ului public Telelink (doar câmpurile folosite; restul se ignoră). Câmpurile pot lipsi, de aceea
 * sunt obiecte, nu primitive. Formatul observat: `docs/ARAD-DATA.md` §Alte date deschise și llms-full.txt din export.
 */
public final class Telelink {

    private Telelink() {}

    /** Un element din `/planner/vehicles`. `/vehicles/route/{id}` poate folosi `currentStopId` în loc de `stopId`. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vehicle(String vehicleId, String label, Integer routeId, Integer patternIndex, String headsign,
                          Integer delaySeconds, OffsetDateTime nextStopArrival, Double latitude, Double longitude,
                          Double heading, OffsetDateTime lastUpdated, Integer stopId, Integer currentStopId) {}

    /** Un grup din `/planner/stops/{stopId}/times`: linia + variantă și sosirile ei. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StopTimes(RouteRef route, List<Time> times) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteRef(Integer routeId, Integer index) {}

    /** Ora estimată = `scheduledArrival` + `arrivalDelay` (secunde, negativ = devans). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Time(Integer stopId, OffsetDateTime scheduledArrival, Integer arrivalDelay, Boolean realtime,
                       String tripId, String headsign) {}
}
