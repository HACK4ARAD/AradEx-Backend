package aradexperience.service.ai;

import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.PlaceType;
import aradexperience.enums.TransportMode;
import aradexperience.service.ai.ItineraryScheduler.Choice;
import aradexperience.service.ai.ItineraryScheduler.Leg;
import aradexperience.service.ai.ItineraryScheduler.Slot;
import aradexperience.service.ai.ItineraryScheduler.Window;
import aradexperience.dto.itineraries.ItineraryStop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * T13 · planificatorul de rezervă (Claude nu răspunde sau răspunde greșit) și textele itinerariului din
 * `ai/fallback/itinerary.json`: propoziția `ask`, titlul, rezumatul, etichetele tipurilor de loc și notele opririlor.
 * Traseul: „cel mai apropiat vecin” ponderat cu interesele — de la punctul de plecare, la fiecare pas oprirea cu cel mai bun
 * scor minus minutele de drum, cât timp încape în fereastră; cel mult un eveniment al zilei (dacă poate fi prins) și o pauză
 * de cafea/masă pe zi (cu interesul „gastronomie” sau la ferestre de peste 5 ore), cu oferta din apropiere dacă există.
 */
@Component
public class ItineraryFallback {

    static final int MAX_STOPS_PER_DAY = 15;
    static final int FOOD_FROM_MINUTES = 300;
    /** O pauză de cafea/masă doar dacă e aproape de oprirea curentă (nu te întorci 30 km pentru o cafea). */
    static final int FOOD_MAX_TRAVEL_MIN = 20;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final JsonNode ro;
    private final JsonNode en;

    public ItineraryFallback() {
        try (InputStream in = getClass().getResourceAsStream("/ai/fallback/itinerary.json")) {
            JsonNode root = new ObjectMapper().readTree(in);
            ro = root.get("ro");
            en = root.get("en");
        } catch (IOException | RuntimeException e) {
            throw new UncheckedIOException("Missing ai/fallback/itinerary.json", e instanceof IOException io ? io : new IOException(e));
        }
    }

    private JsonNode t(String locale) {
        return "en".equals(locale) ? en : ro;
    }

    // ---------------------------------------------------------------- texte

    /** „Jumătate de zi, pe jos, istorie + gastronomie · Vreau neapărat Cetatea”. */
    String ask(String locale, int days, int hours, TransportMode mode, List<String> interests, String freeText) {
        JsonNode t = t(locale);
        StringBuilder sb = new StringBuilder(duration(t.get("durations"), days, hours)).append(", ").append(t.get("modes").get(mode.name()).asText());
        Set<String> labels = new LinkedHashSet<>();
        if (interests != null) {
            for (String raw : interests) {
                if (raw == null || raw.isBlank()) continue;
                String known = Interests.knownName(raw);
                labels.add(known != null && t.get("interests").has(known) ? t.get("interests").get(known).asText() : raw.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!labels.isEmpty()) sb.append(", ").append(String.join(" + ", labels));
        if (freeText != null && !freeText.isBlank()) {
            String ft = freeText.trim().replaceAll("\\s+", " ");
            sb.append(" · ").append(ft.length() > 120 ? ft.substring(0, 119) + "…" : ft);
        }
        String s = sb.toString();
        return s.length() > 300 ? s.substring(0, 299) + "…" : s;
    }

    private static String duration(JsonNode d, int days, int hours) {
        if (days >= 2) return d.get("twoDays").asText();
        if (hours <= 1) return d.get("one").asText();
        if (hours == 4 || hours == 5) return d.get("half").asText();
        if (hours >= 8) return d.get("day").asText();
        return d.get("hours").asText().replace("{h}", String.valueOf(hours));
    }

    String title(String locale, Window w, Interests interests, boolean empty) {
        JsonNode titles = t(locale).get("titles");
        if (empty) return titles.get("empty").asText();
        if (w.mode() == TransportMode.CAR && interests.wine()) return titles.get("wine").asText();
        if (w.days() >= 2) return titles.get("twoDays").asText();
        int h = w.minutesPerDay() / 60;
        return titles.get(h <= 3 ? "short" : h <= 5 ? "half" : "day").asText();
    }

    String summary(String locale, TransportMode mode, List<ItineraryStop> stops, double totalKm) {
        JsonNode t = t(locale);
        if (stops.isEmpty()) return t.get("summaryEmpty").asText();
        ItineraryStop first = stops.get(0);
        String km = "en".equals(locale) ? String.format(Locale.ROOT, "%.1f", totalKm) : String.format(Locale.ROOT, "%.1f", totalKm).replace('.', ',');
        String s = t.get("summary").asText()
                .replace("{stops}", String.valueOf(stops.size()))
                .replace("{mode}", t.get("modes").get(mode.name()).asText())
                .replace("{km}", km)
                .replace("{first}", first.name())
                .replace("{time}", first.arriveAt().atZoneSameInstant(ItineraryScheduler.ARAD).format(HOUR));
        long guides = stops.stream().filter(ItineraryStop::hasGuide).count();
        if (guides == 1) s += t.get("summaryGuide").asText();
        if (guides > 1) s += t.get("summaryGuides").asText().replace("{guides}", String.valueOf(guides));
        return s;
    }

    String typeLabel(String locale, PlaceType type) {
        if (type == null) return null;
        JsonNode n = t(locale).get("types").get(type.name());
        return n == null ? null : n.asText();
    }

    /** Nota opririi: cea a lui Claude dacă există, altfel una utilă din datele noastre (oră, ofertă, ghid, doar exterior). */
    String note(String locale, PlanItem it, String fromChoice) {
        if (fromChoice != null && !fromChoice.isBlank()) return fromChoice.trim();
        JsonNode n = t(locale).get("notes");
        List<String> parts = new ArrayList<>();
        if (it.kind() == ItineraryStopKind.EVENT && it.startAt() != null) {
            parts.add(n.get("event").asText().replace("{time}", it.startAt().atZoneSameInstant(ItineraryScheduler.ARAD).format(HOUR)));
        } else if (it.kind() == ItineraryStopKind.OFFER) {
            parts.add(it.offerLabel() == null || it.offerLabel().isBlank() ? n.get("coffee").asText()
                    : n.get("offer").asText().replace("{label}", it.offerLabel()));
        } else if (it.placeType() == PlaceType.CAFE) {
            parts.add(n.get("coffee").asText());
        } else if (it.placeType() == PlaceType.RESTAURANT) {
            parts.add(n.get("lunch").asText());
        }
        if (it.exteriorOnly()) parts.add(n.get("exterior").asText());
        if (it.guided()) parts.add(n.get("guide").asText());
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    // ---------------------------------------------------------------- traseul

    /**
     * Scorul unui candidat: interesele (4 pe interes atins, cel mult două; −3 dacă omul are interese și locul nu atinge niciunul),
     * ghidul audio, nota, menționarea în textul omului; locurile din județ fără interesul „vin” scad.
     */
    static double score(PlanItem it, Interests interests, String freeTextFolded) {
        int m = interests.matches(it);
        double s = interests.isEmpty() ? 0 : (m == 0 ? -3 : 4.0 * Math.min(m, 2));
        if (it.guided()) s += 2;
        if (it.rating() > 0) s += Math.max(-1, Math.min(1, it.rating() - 4.0));
        if (interests.isEmpty() && it.hasAnyTag("istorie", "arhitectura", "muzee")) s += 1.5;
        if (it.county() && !interests.wine()) s -= 2;
        if (mentioned(it.name(), freeTextFolded)) s += 15; // „vreau neapărat …”: aproape obligatoriu
        return s;
    }

    /** Cât „costă” un minut de drum la alegerea următoarei opriri: pe jos mult, cu mașina puțin. */
    static double travelWeight(TransportMode mode) {
        return switch (mode) {
            case WALK -> 1.0;
            case PUBLIC -> 0.7;
            case CAR -> 0.3;
        };
    }

    private static final Set<String> GENERIC = Set.of("arad", "aradului", "muzeul", "parcul", "palatul", "biserica", "restaurant",
            "pensiunea", "cafenea", "strada", "piata", "centrul", "romano", "catolica", "ortodoxa");

    /** Omul a pomenit locul în text („Vreau neapărat Cetatea”): un cuvânt distinctiv din nume (≥ 5 litere, primele 5) apare în text. */
    static boolean mentioned(String name, String freeTextFolded) {
        if (freeTextFolded == null || freeTextFolded.isBlank() || name == null) return false;
        for (String w : Interests.fold(name).split("[^a-z0-9]+")) {
            if (w.length() >= 5 && !GENERIC.contains(w) && freeTextFolded.contains(w.substring(0, 5))) return true;
        }
        return false;
    }

    /**
     * Opririle alese zi cu zi (fără ore: le pune `ItineraryScheduler`, cu aceleași calcule, deci încap). `sights` = locurile
     * de vizitat, `food` = pauzele (oferte + restaurante/cafenele), `events` = evenimentele din zilele cerute; toate deja
     * filtrate după cât de departe se poate ajunge cu transportul ales.
     */
    List<Choice> route(Window w, Interests interests, String freeText, List<PlanItem> sights, List<PlanItem> food, List<PlanItem> events) {
        String ft = Interests.fold(freeText);
        Set<String> used = new HashSet<>();
        List<Choice> out = new ArrayList<>();
        boolean wantsFood = interests.food() || w.minutesPerDay() >= FOOD_FROM_MINUTES;
        for (int day = 1; day <= w.days(); day++) {
            ZonedDateTime dayStart = w.dayStart(day);
            ZonedDateTime end = w.dayEnd(day);
            PlanItem event = pickEvent(events, used, dayStart, end, interests, ft);
            boolean foodDone = !wantsFood;
            ZonedDateTime foodAfter = dayStart.plusMinutes(Math.min(150, w.minutesPerDay() * 45L / 100));
            ZonedDateTime cursor = dayStart;
            double lat = w.lat(), lng = w.lng();
            boolean first = true;
            int count = 0;
            while (count < MAX_STOPS_PER_DAY) {
                if (event != null && !first) {
                    Leg toEvent = ItineraryScheduler.travel(lat, lng, event, w.mode());
                    if (cursor.plusMinutes(toEvent.minutes()).isAfter(event.startAt().atZoneSameInstant(ItineraryScheduler.ARAD))
                            && ItineraryScheduler.slot(event, cursor.plusMinutes(toEvent.minutes()), end) == null) {
                        event = null; // nu mai poate fi prins
                    }
                }
                PlanItem next = null;
                Slot slot;
                if (!foodDone && !cursor.isBefore(foodAfter)) {
                    next = best(food, used, w, lat, lng, first, cursor, end, event, FOOD_MAX_TRAVEL_MIN, it -> it.kind() == ItineraryStopKind.OFFER ? 5 : 0);
                    if (next != null) foodDone = true;
                }
                if (next == null) next = best(sights, used, w, lat, lng, first, cursor, end, event, Integer.MAX_VALUE, it -> 10 * score(it, interests, ft));
                if (next == null && event != null) {
                    next = event;
                    event = null;
                }
                if (next == null && !foodDone) {
                    next = best(food, used, w, lat, lng, first, cursor, end, null, FOOD_MAX_TRAVEL_MIN, it -> it.kind() == ItineraryStopKind.OFFER ? 5 : 0);
                    foodDone = true;
                }
                if (next == null) break;
                Leg leg = first ? new Leg(0, 0, w.mode()) : ItineraryScheduler.travel(lat, lng, next, w.mode());
                slot = ItineraryScheduler.slot(next, cursor.plusMinutes(leg.minutes()), end);
                if (slot == null) { // doar evenimentul poate ajunge aici fără să încapă
                    used.add(next.key());
                    continue;
                }
                if (next == event) event = null;
                out.add(new Choice(next, day, null));
                used.add(next.key());
                cursor = slot.leave();
                lat = next.lat();
                lng = next.lng();
                first = false;
                count++;
            }
        }
        return out;
    }

    interface Value {
        double of(PlanItem it);
    }

    /**
     * Candidatul cu cea mai mare valoare minus minutele de drum care încape până la sfârșitul zilei și, dacă așteaptă un
     * eveniment, lasă timp să ajungi la el. La prima oprire a zilei drumul contează doar la alegere (orele încep la start).
     */
    private static PlanItem best(List<PlanItem> pool, Set<String> used, Window w, double lat, double lng, boolean first,
                                 ZonedDateTime cursor, ZonedDateTime end, PlanItem event, int maxTravel, Value value) {
        PlanItem best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (PlanItem it : pool) {
            if (used.contains(it.key()) || it == event) continue;
            Leg leg = ItineraryScheduler.travel(lat, lng, it, w.mode());
            if (leg.minutes() > maxTravel) continue;
            ZonedDateTime arrive = cursor.plusMinutes(first ? 0 : leg.minutes());
            Slot s = ItineraryScheduler.slot(it, arrive, end);
            if (s == null) continue;
            if (event != null) {
                ZonedDateTime eventStart = event.startAt().atZoneSameInstant(ItineraryScheduler.ARAD);
                Leg toEvent = ItineraryScheduler.travel(it, event, w.mode());
                if (s.leave().plusMinutes(toEvent.minutes()).isAfter(eventStart)) continue;
            }
            double v = value.of(it) - travelWeight(w.mode()) * leg.minutes();
            if (v > bestValue) {
                bestValue = v;
                best = it;
            }
        }
        return best;
    }

    /**
     * Cel mult un eveniment pe zi: cel cu scorul cel mai mare dintre cele care încep în fereastră (cu măcar 30 min înainte de
     * final) și se potrivesc cu interesele sau cu textul omului (oricare, dacă n-a ales interese).
     */
    private static PlanItem pickEvent(List<PlanItem> events, Set<String> used, ZonedDateTime dayStart, ZonedDateTime end,
                                      Interests interests, String ft) {
        return events.stream()
                .filter(e -> !used.contains(e.key()) && e.startAt() != null)
                .filter(e -> interests.isEmpty() || interests.matches(e) > 0 || mentioned(e.name(), ft))
                .filter(e -> {
                    ZonedDateTime s = e.startAt().atZoneSameInstant(ItineraryScheduler.ARAD);
                    return !s.isBefore(dayStart) && Duration.between(s, end).toMinutes() >= ItineraryScheduler.MIN_VISIT_MIN;
                })
                .max(Comparator.comparingDouble((PlanItem e) -> score(e, interests, ft)).thenComparing(e -> -e.startAt().toEpochSecond()))
                .orElse(null);
    }
}
