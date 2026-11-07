package aradexperience.mapper.itineraries;

import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.itineraries.Itinerary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** Itinerariu ↔ DTO (T13). Opririle se păstrează ca JSON, cu orele în ora Aradului (Europe/Bucharest), ca în răspunsul generat. */
@UtilityClass
public class ItineraryMapper {

    private static final ZoneId ARAD = ZoneId.of("Europe/Bucharest");
    private static final TypeReference<List<ItineraryStop>> STOPS = new TypeReference<>() {};
    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static ItineraryDto toDto(Itinerary i) {
        return new ItineraryDto(i.getId(), i.getShareCode(), i.getTitle(), i.getSummary(), i.getAsk(), i.getDate(), i.getDays(),
                i.getTransport(), i.getStartTime(), i.getTotalMinutes(), i.getTotalKm() == null ? 0 : i.getTotalKm().doubleValue(),
                i.isOffline(), readStops(i.getStops()), i.getCreatedAt() == null ? null : i.getCreatedAt().atZoneSameInstant(ARAD).toOffsetDateTime());
    }

    /** Entitatea nouă din DTO-ul trimis la salvare (fără id, cod, utilizator și dată — le pune service-ul). */
    public static Itinerary toEntity(ItineraryDto d) {
        Itinerary i = new Itinerary();
        i.setTitle(d.title().trim());
        i.setSummary(d.summary());
        i.setAsk(d.ask());
        i.setDate(d.date());
        i.setDays(d.days());
        i.setTransport(d.transport());
        i.setStartTime(d.startTime());
        i.setTotalMinutes(d.totalMinutes());
        i.setTotalKm(BigDecimal.valueOf(d.totalKm()).setScale(1, RoundingMode.HALF_UP));
        i.setOffline(d.offline());
        i.setStops(writeStops(d.stops()));
        return i;
    }

    /** Orele opririlor aduse la ora Aradului (Jackson le citește din cerere în UTC). */
    public static List<ItineraryStop> inArad(List<ItineraryStop> stops) {
        return stops.stream().map(s -> new ItineraryStop(s.order(), s.day(), s.kind(), s.refId(), s.name(), s.subtitle(), s.lat(), s.lng(),
                arad(s.arriveAt()), arad(s.leaveAt()), s.durationMin(), s.travelMinutesFromPrev(), s.travelKmFromPrev(), s.travelMode(),
                s.note(), s.parkingHint(), s.hasGuide(), s.imageUrl())).toList();
    }

    public static String writeStops(List<ItineraryStop> stops) {
        try {
            return JSON.writeValueAsString(inArad(stops));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write itinerary stops", e);
        }
    }

    public static List<ItineraryStop> readStops(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, STOPS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read itinerary stops", e);
        }
    }

    private static OffsetDateTime arad(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ARAD).toOffsetDateTime();
    }
}
