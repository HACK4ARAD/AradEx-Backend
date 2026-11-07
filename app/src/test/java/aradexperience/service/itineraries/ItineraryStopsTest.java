package aradexperience.service.itineraries;

import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.events.Event;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.TransportMode;
import aradexperience.exceptions.BadRequestException;
import aradexperience.mock.ItineraryMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import aradexperience.service.ai.ItineraryFallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryStopsTest {

    static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-14T10:00:00+02:00");

    PlaceRepository places;
    EventRepository events;
    OfferRepository offers;
    BusinessRepository businesses;
    ItineraryStops stops;

    @BeforeEach
    void setUp() {
        places = mock(PlaceRepository.class);
        events = mock(EventRepository.class);
        offers = mock(OfferRepository.class);
        businesses = mock(BusinessRepository.class);
        GuidePointRepository guide = mock(GuidePointRepository.class);
        when(places.findAllById(any())).thenReturn(ItineraryMock.places());
        Event concert = ItineraryMock.event(9, "Concert în curtea Palatului", "2026-11-14T18:00:00+02:00", null);
        concert.setLocationName("Palatul Cultural");
        when(events.findAllById(any())).thenReturn(List.of(concert));
        when(offers.findAllById(any())).thenReturn(List.of(ItineraryMock.coffeeOffer()));
        when(businesses.findAllById(any())).thenReturn(List.of(ItineraryMock.cafeBusiness()));
        when(guide.stats()).thenReturn(ItineraryMock.guides());
        stops = new ItineraryStops(places, events, offers, businesses, guide, new ItineraryFallback());
    }

    static ItineraryStop forged(ItineraryStopKind kind, long refId, String subtitle, String note, String parking) {
        return new ItineraryStop(1, 1, kind, refId, "Nume inventat", subtitle, 1.0, 2.0, AT, AT.plusMinutes(30), 30, 4, 0.3,
                TransportMode.WALK, note, parking, false, "https://rau.example/poza.jpg");
    }

    @Test
    void place_nameCoordsImageGuideAndParking_comeFromTheDatabase_timesFromTheRequest() {
        ItineraryStop s = stops.canonical(List.of(forged(ItineraryStopKind.PLACE, 1, "Text inventat", "O notă", "parcare inventată"))).get(0);

        assertThat(s.name()).isEqualTo("Cetatea Aradului");
        assertThat(s.subtitle()).isEqualTo("Obiectiv turistic");
        assertThat(s.lat()).isEqualTo(46.1709);
        assertThat(s.lng()).isEqualTo(21.3326);
        assertThat(s.hasGuide()).isTrue();
        assertThat(s.parkingHint()).isEqualTo("Piața 13 Martiri (Subcetate)");
        assertThat(s.imageUrl()).isNotEqualTo("https://rau.example/poza.jpg");
        assertThat(s.arriveAt()).isEqualTo(AT);
        assertThat(s.durationMin()).isEqualTo(30);
        assertThat(s.note()).isEqualTo("O notă");
    }

    @Test
    void place_withoutParkingInTheRequest_hasNone_andTheEnglishLabelIsKept() {
        ItineraryStop s = stops.canonical(List.of(forged(ItineraryStopKind.PLACE, 2, "Museum", null, null))).get(0);

        assertThat(s.parkingHint()).isNull();
        assertThat(s.subtitle()).isEqualTo("Museum");
    }

    @Test
    void event_andOffer_comeFromTheDatabase_offerAtTheBusinessLocation() {
        List<ItineraryStop> out = stops.canonical(List.of(forged(ItineraryStopKind.EVENT, 9, "x", null, null),
                forged(ItineraryStopKind.OFFER, 42, "x", null, null)));

        assertThat(out.get(0).name()).isEqualTo("Concert în curtea Palatului");
        assertThat(out.get(0).subtitle()).isEqualTo("Palatul Cultural");
        assertThat(out.get(0).hasGuide()).isTrue();
        assertThat(out.get(1).name()).isEqualTo("10% la orice cafea");
        assertThat(out.get(1).subtitle()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(out.get(1).lat()).isEqualTo(46.1748);
    }

    @Test
    void longNote_isClippedTo300() {
        ItineraryStop s = stops.canonical(List.of(forged(ItineraryStopKind.PLACE, 1, null, "a".repeat(1000), null))).get(0);
        assertThat(s.note()).hasSize(300);
    }

    @Test
    void unknownStop_is400() {
        assertThatThrownBy(() -> stops.canonical(List.of(forged(ItineraryStopKind.PLACE, 999, null, null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> stops.canonical(List.of(forged(ItineraryStopKind.OFFER, 77, null, null, null))))
                .isInstanceOf(BadRequestException.class);
    }
}
