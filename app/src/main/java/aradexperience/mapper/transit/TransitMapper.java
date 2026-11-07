package aradexperience.mapper.transit;

import aradexperience.dto.transit.TransitArrivalDto;
import aradexperience.dto.transit.TransitPatternDto;
import aradexperience.dto.transit.TransitRouteDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehicleDto;
import aradexperience.entity.transit.TransitPattern;
import aradexperience.entity.transit.TransitRoute;
import aradexperience.entity.transit.TransitStop;
import aradexperience.service.transit.Telelink;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Entitățile statice (V12) și răspunsurile Telelink → DTO-urile din docs/API.md §Transport public (T22). */
@UtilityClass
public class TransitMapper {

    /** Peste 120 s de la `lastUpdated` poziția unui vehicul e învechită (convenția din ghidul exportului). */
    public static final Duration VEHICLE_STALE = Duration.ofSeconds(120);

    public static TransitStopDto toStopDto(TransitStop s, List<Integer> routeIds, List<String> lines) {
        return new TransitStopDto(s.getId(), s.getName(), s.getCode(), s.getLat(), s.getLng(), routeIds, lines);
    }

    public static TransitRouteDto toRouteDto(TransitRoute r, List<TransitPattern> patterns) {
        return new TransitRouteDto(r.getId(), r.getShortName(), r.getLongName(), r.getMode(), r.getColor(), r.getTextColor(),
                patterns.stream().sorted(Comparator.comparing(TransitPattern::getPatternIndex)).map(TransitMapper::toPatternDto).toList());
    }

    public static TransitPatternDto toPatternDto(TransitPattern p) {
        return new TransitPatternDto(p.getPatternIndex(), p.getFromStopId(), p.getToStopId(), List.copyOf(p.getStopIds()), decodePath(p.getPath()));
    }

    /** Polilinie Google (precizie 5) → puncte `[lat, lng]`. Șir gol / null → listă goală. */
    public static List<double[]> decodePath(String encoded) {
        List<double[]> pts = new ArrayList<>();
        if (encoded == null) return pts;
        int i = 0, lat = 0, lng = 0;
        while (i < encoded.length()) {
            int[] delta = new int[2];
            for (int k = 0; k < 2; k++) {
                int shift = 0, result = 0, b;
                do {
                    b = encoded.charAt(i++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                delta[k] = (result & 1) != 0 ? ~(result >> 1) : result >> 1;
            }
            lat += delta[0];
            lng += delta[1];
            pts.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return pts;
    }

    /** `route` = linia din datele statice, null dacă Telelink are o linie nouă (atunci lipsesc numărul și culoarea). */
    public static TransitVehicleDto toVehicleDto(Telelink.Vehicle v, TransitRoute route, Instant now) {
        boolean stale = v.lastUpdated() == null || v.lastUpdated().toInstant().plus(VEHICLE_STALE).isBefore(now);
        return new TransitVehicleDto(v.vehicleId(), v.label(), v.routeId(),
                route == null ? null : route.getShortName(), route == null ? null : route.getMode(), route == null ? null : route.getColor(),
                blankToNull(v.headsign()), v.latitude(), v.longitude(), v.heading(), v.delaySeconds(),
                v.stopId() != null ? v.stopId() : v.currentStopId(), v.nextStopArrival(), v.lastUpdated(), stale);
    }

    /** Ora estimată = programat + întârziere; `minutes` rotunjit în jos, niciodată negativ. */
    public static TransitArrivalDto toArrivalDto(Telelink.Time t, int routeId, TransitRoute route, int stopId, Instant now) {
        int delay = t.arrivalDelay() == null ? 0 : t.arrivalDelay();
        Instant at = expectedAt(t);
        int minutes = (int) Math.max(0, Duration.between(now, at).toMinutes());
        return new TransitArrivalDto(routeId, route == null ? null : route.getShortName(), route == null ? null : route.getMode(),
                route == null ? null : route.getColor(), blankToNull(t.headsign()), t.stopId() != null ? t.stopId() : stopId,
                at.atOffset(ZoneOffset.UTC), minutes, delay, Boolean.TRUE.equals(t.realtime()));
    }

    public static Instant expectedAt(Telelink.Time t) {
        return t.scheduledArrival().toInstant().plusSeconds(t.arrivalDelay() == null ? 0 : t.arrivalDelay());
    }

    public static OffsetDateTime utc(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
