package aradexperience.dto.itineraries;

import aradexperience.dto.DtoJson;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.TransportMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** T13: DTO-urile planificatorului (docs/API.md §AI). */
class ItineraryDtosTest {

    @Test
    void itineraryRequest_readsDateTimeTransportInterestsAndStart() {
        ItineraryRequest r = DtoJson.fromResource("dto/itineraries/itinerary-request.json", ItineraryRequest.class);
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(r.startTime()).isEqualTo("10:00");
        assertThat(r.hours()).isEqualTo(4);
        assertThat(r.days()).isNull();
        assertThat(r.transport()).isEqualTo(TransportMode.WALK);
        assertThat(r.interests()).containsExactly("istorie", "gastronomie");
        assertThat(r.start().lat()).isEqualTo(46.1754);
        assertThat(r.locale()).isEqualTo("ro");
    }

    @Test
    void itineraryDto_readsStopsWithTimesTravelAndGuide() {
        ItineraryDto d = DtoJson.fromResource("dto/itineraries/itinerary-dto.json", ItineraryDto.class);
        assertThat(d.shareCode()).isEqualTo("AX-I-7K3Q9Z");
        assertThat(d.ask()).contains("Cetatea");
        assertThat(d.stops()).hasSize(3);
        ItineraryStop second = d.stops().get(1);
        assertThat(second.kind()).isEqualTo(ItineraryStopKind.PLACE);
        assertThat(second.arriveAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T10:36:00+02:00"));
        assertThat(second.travelMinutesFromPrev()).isEqualTo(21);
        assertThat(second.hasGuide()).isTrue();
        assertThat(d.stops().get(2).kind()).isEqualTo(ItineraryStopKind.OFFER);
        assertThat(d.stops().get(2).imageUrl()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "dto/itineraries/itinerary-request.json, aradexperience.dto.itineraries.ItineraryRequest",
            "dto/itineraries/itinerary-dto.json, aradexperience.dto.itineraries.ItineraryDto",
    })
    void roundTrip_matchesJson(String json, Class<?> type) throws Exception {
        Object dto = DtoJson.fromResource(json, type);
        JSONAssert.assertEquals(DtoJson.read(json), DtoJson.APP.writeValueAsString(dto), JSONCompareMode.STRICT);
    }
}
