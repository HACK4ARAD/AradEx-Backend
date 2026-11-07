package aradexperience.service.ai;

import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.TransportMode;
import aradexperience.util.Geo;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * T13 · orele itinerariului, calculate de BE (Claude doar alege și ordonează opririle). Drumul = haversine × factor de ocol /
 * viteză: pe jos 1,3 / 4,5 km/h; mașina 1,4 / 25 km/h + 8 min de parcare (sub 0,7 km se merge pe jos); transport public
 * 1,3 / 14 km/h + 6 min de așteptare peste 1,2 km, altfel pe jos. Evenimentele au oră fixă: dacă ajungi după start, oprirea
 * se scoate (excepție: evenimentele lungi, de peste 2 ore — târguri, festivaluri —, unde intri și mai târziu); dacă ajungi înainte, aștepți.
 * O oprire care nu mai încape în fereastra zilei se scurtează (dacă rămân măcar 30 de minute) sau se sare.
 */
@UtilityClass
public class ItineraryScheduler {

    public static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");

    static final double WALK_DETOUR = 1.3, WALK_KMH = 4.5;
    static final double CAR_DETOUR = 1.4, CAR_KMH = 25, CAR_WALK_BELOW_KM = 0.7;
    static final int CAR_PARKING_MIN = 8;
    static final double PUBLIC_DETOUR = 1.3, PUBLIC_KMH = 14, PUBLIC_WALK_BELOW_KM = 1.2;
    static final int PUBLIC_WAIT_MIN = 6;
    static final int EVENT_DEFAULT_MIN = 90, EVENT_MAX_MIN = 120, OFFER_MIN = 30, DEFAULT_VISIT_MIN = 60, MIN_VISIT_MIN = 30;

    /** Fereastra cerută: ziua, ora de start, câte zile, câte minute pe zi, transportul și punctul de plecare. */
    public record Window(LocalDate date, LocalTime start, int days, int minutesPerDay, TransportMode mode, double lat, double lng) {
        ZonedDateTime dayStart(int day) {
            return date.plusDays(day - 1L).atTime(start).atZone(ARAD);
        }

        ZonedDateTime dayEnd(int day) {
            return dayStart(day).plusMinutes(minutesPerDay);
        }
    }

    /** O oprire aleasă (de Claude sau de planificatorul de rezervă), în ordinea vizitei. */
    record Choice(PlanItem item, int day, String note) {}

    /** Un drum între două opriri. */
    record Leg(int minutes, double km, TransportMode mode) {}

    /** Rezultatul: opririle cu ore + totalurile. */
    public record Schedule(List<ItineraryStop> stops, int totalMinutes, double totalKm) {}

    /** Notele implicite / indicațiile de parcare vin de la apelant (depind de limbă). */
    interface Notes {
        String note(PlanItem item, String fromChoice);
    }

    static Leg travel(double fromLat, double fromLng, PlanItem to, TransportMode mode) {
        double straight = Geo.distanceKm(fromLat, fromLng, to.lat(), to.lng());
        if (straight < 0.05) return new Leg(0, 0, mode == TransportMode.CAR ? TransportMode.WALK : mode);
        return switch (mode) {
            case WALK -> walk(straight);
            case CAR -> {
                double km = straight * CAR_DETOUR;
                yield straight * WALK_DETOUR < CAR_WALK_BELOW_KM ? walk(straight)
                        : new Leg(minutes(km, CAR_KMH) + CAR_PARKING_MIN, km, TransportMode.CAR);
            }
            case PUBLIC -> {
                double km = straight * PUBLIC_DETOUR;
                yield km <= PUBLIC_WALK_BELOW_KM ? walk(straight) : new Leg(minutes(km, PUBLIC_KMH) + PUBLIC_WAIT_MIN, km, TransportMode.PUBLIC);
            }
        };
    }

    static Leg travel(PlanItem from, PlanItem to, TransportMode mode) {
        return travel(from.lat(), from.lng(), to, mode);
    }

    private static Leg walk(double straight) {
        double km = straight * WALK_DETOUR;
        return new Leg(minutes(km, WALK_KMH), km, TransportMode.WALK);
    }

    private static int minutes(double km, double kmh) {
        return Math.max(1, (int) Math.ceil(km / kmh * 60));
    }

    /** Durata vizitei fără constrângeri: loc = `visitMinutes` (60 implicit), ofertă = 30, eveniment = până la final (90 dacă nu se știe, cel mult 120). */
    static int visitMinutes(PlanItem it) {
        return switch (it.kind()) {
            case OFFER -> OFFER_MIN;
            case PLACE -> it.visitMinutes() > 0 ? it.visitMinutes() : DEFAULT_VISIT_MIN;
            case EVENT -> eventMinutes(it);
        };
    }

    private static int eventMinutes(PlanItem it) {
        if (it.endAt() == null || !it.endAt().isAfter(it.startAt())) return EVENT_DEFAULT_MIN;
        long m = Duration.between(it.startAt(), it.endAt()).toMinutes();
        return (int) Math.max(MIN_VISIT_MIN, Math.min(EVENT_MAX_MIN, m));
    }

    /** Ora de sosire și durata unei opriri, sau null dacă oprirea nu încape / evenimentul nu mai poate fi prins. */
    record Slot(ZonedDateTime arrive, int minutes) {
        ZonedDateTime leave() {
            return arrive.plusMinutes(minutes);
        }
    }

    static Slot slot(PlanItem it, ZonedDateTime arrive, ZonedDateTime dayEnd) {
        int duration = visitMinutes(it);
        if (it.kind() == ItineraryStopKind.EVENT) {
            ZonedDateTime start = it.startAt().atZoneSameInstant(ARAD);
            if (arrive.isAfter(start)) {
                // a început deja: doar la evenimentele lungi (târguri, festivaluri) se mai intră
                ZonedDateTime end = it.endAt() == null ? null : it.endAt().atZoneSameInstant(ARAD);
                if (end == null || Duration.between(start, end).toMinutes() < EVENT_MAX_MIN) return null;
                long left = Duration.between(arrive, end).toMinutes();
                if (left < MIN_VISIT_MIN) return null;
                duration = (int) Math.min(EVENT_DEFAULT_MIN, left);
            } else {
                arrive = start;
            }
        }
        if (arrive.plusMinutes(duration).isAfter(dayEnd)) {
            long fit = Duration.between(arrive, dayEnd).toMinutes();
            if (fit < Math.min(MIN_VISIT_MIN, duration) || fit <= 0) return null;
            duration = (int) fit;
        }
        return new Slot(arrive, duration);
    }

    /**
     * Orele tuturor opririlor, zi cu zi: prima oprire a zilei începe la ora de start (sau la ora evenimentului), fără drum;
     * fiecare următoare după drumul de la cea anterioară. Opririle care nu încap se scot. `order` = 1..n peste zile.
     */
    static Schedule schedule(List<Choice> choices, Window w, Notes notes) {
        List<ItineraryStop> out = new ArrayList<>();
        int totalMinutes = 0;
        double totalKm = 0;
        for (int day = 1; day <= w.days(); day++) {
            ZonedDateTime cursor = w.dayStart(day);
            ZonedDateTime end = w.dayEnd(day);
            PlanItem prev = null;
            ZonedDateTime firstArrive = null;
            for (Choice c : choices) {
                if (c.day() != day) continue;
                PlanItem it = c.item();
                Leg leg = prev == null ? new Leg(0, 0, w.mode()) : travel(prev, it, w.mode());
                Slot s = slot(it, cursor.plusMinutes(leg.minutes()), end);
                if (s == null) continue;
                // indicația de parcare doar unde chiar ajungi cu mașina (nu la opririle apropiate, făcute pe jos)
                boolean car = w.mode() == TransportMode.CAR && it.kind() == ItineraryStopKind.PLACE
                        && (prev == null || leg.mode() == TransportMode.CAR);
                out.add(new ItineraryStop(out.size() + 1, day, it.kind(), it.refId(), it.name(), it.subtitle(), it.lat(), it.lng(),
                        s.arrive().toOffsetDateTime(), s.leave().toOffsetDateTime(), s.minutes(), leg.minutes(), Geo.round1(leg.km()),
                        leg.mode(), notes.note(it, c.note()), car ? clip500(blankToNull(it.parkingHint())) : null, it.guided(), it.imageUrl()));
                if (firstArrive == null) firstArrive = s.arrive();
                totalKm += leg.km();
                cursor = s.leave();
                prev = it;
            }
            if (firstArrive != null) totalMinutes += (int) Duration.between(firstArrive, cursor).toMinutes();
        }
        return new Schedule(List.copyOf(out), totalMinutes, Geo.round1(totalKm));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** `ItineraryStop.parkingHint` acceptă cel mult 500 de caractere (altfel salvarea ar da 400). */
    static String clip500(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500).strip();
    }
}
