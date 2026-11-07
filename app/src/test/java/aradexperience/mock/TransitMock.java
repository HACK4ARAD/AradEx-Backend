package aradexperience.mock;

import aradexperience.entity.transit.TransitPattern;
import aradexperience.entity.transit.TransitRoute;
import aradexperience.entity.transit.TransitStop;
import aradexperience.enums.TransitMode;
import aradexperience.service.transit.Telelink;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Date de test T22: rețea mică (tramvaiul 1 = routeId 11, autobuzul 21 = routeId 3), două peroane „Podgoria”. */
public final class TransitMock {

    public static final Instant NOW = Instant.parse("2026-11-07T15:12:10Z");

    private TransitMock() {}

    public static TransitRoute tram1() {
        return route(11, "1", TransitMode.TRAM, "#b80000");
    }

    public static TransitRoute bus21() {
        return route(3, "21", TransitMode.BUS, "#5300eb");
    }

    public static TransitRoute route(int id, String shortName, TransitMode mode, String color) {
        TransitRoute r = new TransitRoute();
        r.setId(id);
        r.setShortName(shortName);
        r.setLongName("Linia " + shortName);
        r.setMode(mode);
        r.setColor(color);
        r.setTextColor("#ffffff");
        return r;
    }

    /** 120 Podgoria (nord), 121 Podgoria (peronul de vizavi, ~60 m), 130 Primăria (~1 km sud). */
    public static List<TransitStop> stops() {
        return List.of(stop(120, "Podgoria", 46.18338, 21.32509), stop(121, "Podgoria", 46.18390, 21.32540),
                stop(130, "Primaria", 46.1754, 21.3193));
    }

    public static TransitStop stop(int id, String name, double lat, double lng) {
        TransitStop s = new TransitStop();
        s.setId(id);
        s.setName(name);
        s.setCode(String.valueOf(id));
        s.setLat(lat);
        s.setLng(lng);
        return s;
    }

    public static List<TransitPattern> patterns() {
        return List.of(pattern(1, 11, 1, 120, 130), pattern(2, 11, -1, 130, 121), pattern(3, 3, 12, 130));
    }

    /** `path` = două puncte, codificate (46.18338,21.32509 → 46.18384,21.32568). */
    public static TransitPattern pattern(int id, int routeId, int index, Integer... stopIds) {
        TransitPattern p = new TransitPattern();
        p.setId(id);
        p.setRouteId(routeId);
        p.setPatternIndex(index);
        p.setFromStopId(stopIds[0]);
        p.setToStopId(stopIds[stopIds.length - 1]);
        p.setPath("ce{xGy`daC{AuB");
        p.setStopIds(new ArrayList<>(List.of(stopIds)));
        return p;
    }

    public static Telelink.Vehicle vehicle(String id, Integer routeId, Instant lastUpdated) {
        return new Telelink.Vehicle(id, "L" + id, routeId, 1, "Piata Romana", -30, OffsetDateTime.parse("2026-11-07T15:13:00Z"),
                46.1849594, 21.3097095, 3.2, lastUpdated == null ? null : lastUpdated.atOffset(ZoneOffset.UTC), 120, null);
    }

    /** O sosire peste `minutes` minute de la NOW, fără întârziere. */
    public static Telelink.StopTimes times(int routeId, int stopId, int... minutes) {
        List<Telelink.Time> ts = new ArrayList<>();
        for (int m : minutes) {
            ts.add(new Telelink.Time(stopId, NOW.plusSeconds(m * 60L).atOffset(ZoneOffset.UTC), 0, true, "t" + m, "Spre centru"));
        }
        return new Telelink.StopTimes(new Telelink.RouteRef(routeId, 1), ts);
    }

    /** Ceas pe care testele îl mută înainte. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant now) {
            this.now = now;
        }

        public void advanceSeconds(long s) {
            now = now.plusSeconds(s);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
