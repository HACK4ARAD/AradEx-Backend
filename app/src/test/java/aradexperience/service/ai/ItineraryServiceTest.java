package aradexperience.service.ai;

import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.places.Place;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.TransportMode;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.mock.ItineraryMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import aradexperience.util.Geo;
import com.anthropic.models.messages.JsonOutputFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryServiceTest {

    static final OffsetDateTime START = OffsetDateTime.parse("2026-11-14T10:00:00+02:00");

    ClaudeClient claude;
    PlaceRepository places;
    GuidePointRepository guide;
    EventRepository events;
    OfferRepository offers;
    BusinessRepository businesses;
    ItineraryService service;
    final List<Place> placeList = new ArrayList<>();
    final List<Event> eventList = new ArrayList<>();
    final List<Offer> offerList = new ArrayList<>();

    @BeforeEach
    void setUp() {
        claude = mock(ClaudeClient.class);
        places = mock(PlaceRepository.class);
        guide = mock(GuidePointRepository.class);
        events = mock(EventRepository.class);
        offers = mock(OfferRepository.class);
        businesses = mock(BusinessRepository.class);
        placeList.addAll(ItineraryMock.places());
        when(places.findAll()).thenAnswer(inv -> placeList);
        when(guide.stats()).thenReturn(ItineraryMock.guides());
        when(events.findCandidates(any(), any())).thenAnswer(inv -> eventList);
        when(offers.findAvailable(any())).thenAnswer(inv -> offerList);
        when(businesses.findAllById(any())).thenReturn(List.of(ItineraryMock.cafeBusiness()));
        when(claude.json(anyString(), anyString(), any(), anyLong())).thenReturn(Optional.empty());
        service = new ItineraryService(claude, places, guide, events, offers, businesses, new ItineraryFallback(), new ItineraryRateLimiter());
        // azi e 7 nov. 2026; cererile din teste sunt pe 14 nov.
        service.setNowClock(Clock.fixed(Instant.parse("2026-11-07T08:00:00Z"), ZoneOffset.UTC));
    }

    private void claudeAnswers(String json) {
        when(claude.json(anyString(), anyString(), any(), anyLong())).thenReturn(Optional.of(json));
    }

    private static String stops(String... kindIdDay) {
        List<String> out = new ArrayList<>();
        for (String s : kindIdDay) {
            String[] p = s.split(":");
            out.add("{\"day\":" + (p.length > 2 ? p[2] : "1") + ",\"kind\":\"" + p[0] + "\",\"refId\":" + p[1] + ",\"note\":\"Nota " + p[1] + "\"}");
        }
        return "{\"title\":\"Centrul istoric\",\"summary\":\"Un tur scurt.\",\"stops\":[" + String.join(",", out) + "]}";
    }

    /** Fiecare zi: prima oprire la/după start, fiecare sosire după plecarea anterioară + drum, totul în fereastră; order = 1..n. */
    private static void assertTimeline(ItineraryDto d, int minutesPerDay) {
        for (int i = 0; i < d.stops().size(); i++) {
            ItineraryStop s = d.stops().get(i);
            assertThat(s.order()).isEqualTo(i + 1);
            OffsetDateTime dayStart = START.plusDays(s.day() - 1L);
            assertThat(s.arriveAt()).isAfterOrEqualTo(dayStart);
            assertThat(s.leaveAt()).isBeforeOrEqualTo(dayStart.plusMinutes(minutesPerDay));
            assertThat(Duration.between(s.arriveAt(), s.leaveAt()).toMinutes()).isEqualTo(s.durationMin()).isPositive();
            assertThat(s.arriveAt().getOffset()).isEqualTo(START.getOffset());
            if (i > 0 && d.stops().get(i - 1).day() == s.day()) {
                ItineraryStop prev = d.stops().get(i - 1);
                assertThat(s.arriveAt()).isAfterOrEqualTo(prev.leaveAt().plusMinutes(s.travelMinutesFromPrev()));
            } else {
                assertThat(s.travelMinutesFromPrev()).isZero();
            }
        }
    }

    @Test
    void claude_unknownIdsAndDuplicatesAreDropped_knownOnesKeepClaudesOrderAndNotes() {
        claudeAnswers(stops("PLACE:4", "PLACE:999", "EVENT:555", "OFFER:4", "PLACE:2", "PLACE:4", "BOGUS:1"));

        ItineraryDto d = service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of("history"), null), "k");

        assertThat(d.offline()).isFalse();
        assertThat(d.title()).isEqualTo("Centrul istoric");
        assertThat(d.stops()).extracting(ItineraryStop::refId).containsExactly(4L, 2L);
        assertThat(d.stops().get(0).note()).isEqualTo("Nota 4");
        assertThat(d.stops().get(1).subtitle()).isEqualTo("Muzeu");
        assertThat(d.stops().get(1).hasGuide()).isTrue();
        assertThat(d.id()).isNull();
        assertThat(d.shareCode()).isNull();
    }

    @Test
    void claude_timesAreComputedByTheBackend_neverOverlap_andStayInsideTheWindow() {
        claudeAnswers(stops("PLACE:4", "PLACE:20", "PLACE:2", "PLACE:7", "PLACE:3", "PLACE:1", "PLACE:30"));

        ItineraryDto d = service.plan(ItineraryMock.request(3, null, TransportMode.WALK, List.of(), null), "k");

        assertTimeline(d, 180);
        ItineraryStop first = d.stops().get(0);
        assertThat(first.arriveAt()).isEqualTo(START);
        assertThat(first.leaveAt()).isEqualTo(START.plusMinutes(15));
        // Primăria → Palatul Neuman: ~0,16 km în linie dreaptă × 1,3 la 4,5 km/h → 3 min
        ItineraryStop second = d.stops().get(1);
        assertThat(second.travelMode()).isEqualTo(TransportMode.WALK);
        assertThat(second.travelMinutesFromPrev()).isBetween(2, 4);
        assertThat(second.arriveAt()).isEqualTo(first.leaveAt().plusMinutes(second.travelMinutesFromPrev()));
        // fereastra de 3 h nu le încape pe toate: ultimele se scurtează sau se scot
        assertThat(d.stops().size()).isLessThan(7);
        assertThat(d.totalMinutes()).isLessThanOrEqualTo(180);
        assertThat(d.totalKm()).isEqualTo(Geo.round1(d.stops().stream().mapToDouble(ItineraryStop::travelKmFromPrev).sum()), org.assertj.core.data.Offset.offset(0.2));
    }

    @Test
    void claude_anEventThatCannotBeReachedInTime_isDropped_oneLaterIsWaitedFor() {
        eventList.add(ItineraryMock.event(55, "Concert la prânz", "2026-11-14T10:30:00+02:00", "2026-11-14T11:30:00+02:00"));
        eventList.add(ItineraryMock.event(56, "Concert după-amiază", "2026-11-14T13:00:00+02:00", null));
        claudeAnswers(stops("PLACE:2", "EVENT:55", "PLACE:4", "EVENT:56"));

        ItineraryDto d = service.plan(ItineraryMock.request(6, null, TransportMode.WALK, List.of("art"), null), "k");

        assertThat(d.stops()).extracting(ItineraryStop::refId).containsExactly(2L, 4L, 56L);
        ItineraryStop concert = d.stops().get(2);
        assertThat(concert.kind()).isEqualTo(ItineraryStopKind.EVENT);
        assertThat(concert.arriveAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T13:00:00+02:00"));
        assertThat(concert.durationMin()).isEqualTo(90);
        assertThat(concert.subtitle()).isEqualTo("Palatul Cultural");
        assertThat(concert.hasGuide()).isTrue(); // evenimentul e la Palatul Cultural, care are ghid
        assertTimeline(d, 360);
    }

    @Test
    void claude_byCar_addsParkingMinutes_andTheParkingHint_shortHopsAreWalked() {
        claudeAnswers(stops("PLACE:4", "PLACE:20", "PLACE:11"));

        ItineraryDto d = service.plan(ItineraryMock.request(8, null, TransportMode.CAR, List.of("wine"), null), "k");

        assertThat(d.stops()).extracting(ItineraryStop::refId).containsExactly(4L, 20L, 11L);
        assertThat(d.stops().get(0).parkingHint()).isEqualTo("Parcările cu plată de pe Bd. Revoluției");
        assertThat(d.stops().get(1).travelMode()).isEqualTo(TransportMode.WALK); // 200 m: nu muți mașina
        ItineraryStop minis = d.stops().get(2);
        assertThat(minis.travelMode()).isEqualTo(TransportMode.CAR);
        double km = Geo.distanceKm(46.1754, 21.3172, 46.1378, 21.6048) * 1.4;
        assertThat(minis.travelMinutesFromPrev()).isEqualTo((int) Math.ceil(km / 25 * 60) + 8);
        assertThat(minis.travelKmFromPrev()).isEqualTo(Geo.round1(km));
        assertThat(minis.parkingHint()).isEqualTo("Parcarea muzeului din Miniș");
        assertTimeline(d, 480);
    }

    @Test
    void claude_twoDays_stopsOnDayOneAndDayTwo_dayTwoStartsAgainAtStartTime() {
        claudeAnswers(stops("PLACE:4:1", "PLACE:2:1", "PLACE:1:2", "PLACE:7:2", "PLACE:3:3"));

        ItineraryDto d = service.plan(ItineraryMock.request(null, 2, TransportMode.WALK, List.of("history"), null), "k");

        assertThat(d.days()).isEqualTo(2);
        assertThat(d.stops()).extracting(ItineraryStop::day).containsExactly(1, 1, 2, 2);
        assertThat(d.stops()).extracting(ItineraryStop::order).containsExactly(1, 2, 3, 4);
        assertThat(d.stops().get(2).arriveAt()).isEqualTo(START.plusDays(1));
        assertThat(d.stops().get(2).travelMinutesFromPrev()).isZero();
        assertTimeline(d, 480);
    }

    @Test
    void claudeDown_offlineFallback_fourHoursOnFootInTheCentre_hasStopsInsideTheWindow_noCountyPlaces() {
        ItineraryDto d = service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of("history"), null), "k");

        assertThat(d.offline()).isTrue();
        assertThat(d.stops().size()).isGreaterThanOrEqualTo(2);
        assertThat(d.stops()).extracting(ItineraryStop::refId).doesNotContain(11L);
        assertThat(d.stops().get(0).hasGuide()).isTrue();
        assertThat(d.title()).isEqualTo("Jumătate de zi prin centrul istoric");
        assertThat(d.summary()).contains("opriri pe jos").contains(d.stops().get(0).name());
        assertThat(d.ask()).isEqualTo("Jumătate de zi, pe jos, istorie");
        assertThat(d.startTime()).isEqualTo("10:00");
        assertTimeline(d, 240);
    }

    @Test
    void claudeInvalidJson_isTheOfflineFallback_andAnEmptyChoiceToo() {
        claudeAnswers("nu e json");
        assertThat(service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of(), null), "k").offline()).isTrue();
        claudeAnswers(stops("PLACE:999"));
        assertThat(service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of(), null), "k").offline()).isTrue();
    }

    @Test
    void offline_twoDays_fillsBothDays_withoutRepeatingAPlace() {
        placeList.clear();
        placeList.addAll(ItineraryMock.manyPlaces());

        ItineraryDto d = service.plan(ItineraryMock.request(null, 2, TransportMode.WALK, List.of(), null), "k");

        assertThat(d.offline()).isTrue();
        assertThat(d.stops()).extracting(ItineraryStop::day).contains(1, 2);
        assertThat(d.stops()).extracting(ItineraryStop::refId).doesNotHaveDuplicates();
        assertThat(d.title()).isEqualTo("Două zile în Arad și împrejurimi");
        assertTimeline(d, 480);
    }

    @Test
    void offline_includesTheEventOfTheDay_andACoffeeOffer_withTheOfferIdAndTheBusinessLocation() {
        eventList.add(ItineraryMock.event(56, "Concert după-amiază", "2026-11-14T13:00:00+02:00", "2026-11-14T14:30:00+02:00"));
        offerList.add(ItineraryMock.coffeeOffer());

        ItineraryDto d = service.plan(ItineraryMock.request(6, null, TransportMode.WALK, List.of("history", "food", "art"), null), "k");

        assertThat(d.stops()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ItineraryStopKind.EVENT);
            assertThat(s.refId()).isEqualTo(56L);
            assertThat(s.arriveAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T13:00:00+02:00"));
        });
        assertThat(d.stops()).anySatisfy(s -> {
            assertThat(s.kind()).isEqualTo(ItineraryStopKind.OFFER);
            assertThat(s.refId()).isEqualTo(42L);
            assertThat(s.name()).isEqualTo("10% la orice cafea");
            assertThat(s.subtitle()).isEqualTo("Cafeneaua din Pasaj");
            assertThat(s.lat()).isEqualTo(46.1748);
            assertThat(s.durationMin()).isEqualTo(30);
            assertThat(s.note()).isEqualTo("Ofertă în aplicație: -10%.");
        });
        assertThat(d.stops()).filteredOn(s -> s.kind() == ItineraryStopKind.OFFER).hasSize(1);
        assertTimeline(d, 360);
    }

    @Test
    void offline_byCarWithWine_goesToTheWinery_withParking() {
        ItineraryDto d = service.plan(ItineraryMock.request(8, null, TransportMode.CAR, List.of("wine"), null), "k");

        assertThat(d.title()).isEqualTo("Pe drumul vinului, cu mașina");
        assertThat(d.stops()).anySatisfy(s -> {
            assertThat(s.refId()).isEqualTo(11L);
            assertThat(s.parkingHint()).isEqualTo("Parcarea muzeului din Miniș");
        });
        assertTimeline(d, 480);
    }

    @Test
    void offline_freeTextMention_putsThatPlaceIn() {
        ItineraryDto d = service.plan(ItineraryMock.request(2, null, TransportMode.WALK, List.of("museums"), "Vreau neapărat Cetatea"), "k");

        assertThat(d.stops()).extracting(ItineraryStop::refId).contains(1L);
        assertThat(d.ask()).isEqualTo("2 ore, pe jos, muzee · Vreau neapărat Cetatea");
    }

    @Test
    void ask_mapsTheAppInterestIds_andKeepsFreeWords_inEnglishToo() {
        ItineraryDto ro = service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of("history", "food", "crame & vin", "Fotbal"), "Vreau Cetatea"), "k");
        assertThat(ro.ask()).isEqualTo("Jumătate de zi, pe jos, istorie + gastronomie + crame & vin + fotbal · Vreau Cetatea");

        var en = new aradexperience.dto.itineraries.ItineraryRequest(ItineraryMock.DATE, null, null, 2, TransportMode.PUBLIC, List.of("nature"), null, null, "en");
        ItineraryDto d = service.plan(en, "k");
        assertThat(d.ask()).isEqualTo("Two days, by public transport, nature");
        assertThat(d.startTime()).isEqualTo("10:00");
    }

    @Test
    void prompt_hasTheCatalogInReach_theDayEvents_andTheSchema() {
        eventList.add(ItineraryMock.event(56, "Concert după-amiază", "2026-11-14T13:00:00+02:00", null));
        eventList.add(ItineraryMock.event(57, "Concert săptămâna viitoare", "2026-11-21T13:00:00+02:00", null));
        service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of("history"), "Vreau Cetatea"), "k");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonOutputFormat.Schema> schema = ArgumentCaptor.forClass(JsonOutputFormat.Schema.class);
        verify(claude).json(system.capture(), user.capture(), schema.capture(), anyLong());
        assertThat(system.getValue()).contains("Cetatea Aradului").contains("Palatul Cultural").contains("ghid").contains("Concert după-amiază")
                .doesNotContain("Miniș").doesNotContain("săptămâna viitoare").contains("română");
        assertThat(system.getValue().lines().count()).isLessThanOrEqualTo(120);
        assertThat(user.getValue()).contains("Vreau Cetatea").contains("240 de minute").contains("WALK");
        assertThat(schema.getValue()).isSameAs(ItineraryService.SCHEMA);
    }

    @Test
    void moreThan20RequestsAMinuteFromTheSameClient_is429() {
        for (int i = 0; i < 20; i++) service.plan(ItineraryMock.request(1, null, TransportMode.WALK, List.of(), null), "same");
        assertThatThrownBy(() -> service.plan(ItineraryMock.request(1, null, TransportMode.WALK, List.of(), null), "same"))
                .isInstanceOf(TooManyRequestsException.class);
        service.plan(ItineraryMock.request(1, null, TransportMode.WALK, List.of(), null), "other");
    }

    @Test
    void today_atAStartTimeAlreadyPassed_startsNow_roundedUpToTheNextQuarter() {
        // 14 nov., 11:07 la Arad; cererea zice 10:00
        service.setNowClock(Clock.fixed(Instant.parse("2026-11-14T09:07:00Z"), ZoneOffset.UTC));
        claudeAnswers(stops("PLACE:4", "PLACE:1"));

        ItineraryDto d = service.plan(ItineraryMock.request(3, null, TransportMode.WALK, List.of(), null), "k");

        assertThat(d.stops().get(0).arriveAt()).isEqualTo(OffsetDateTime.parse("2026-11-14T11:15:00+02:00"));
    }

    @Test
    void today_atAStartTimeStillAhead_keepsIt() {
        service.setNowClock(Clock.fixed(Instant.parse("2026-11-14T06:30:00Z"), ZoneOffset.UTC));
        claudeAnswers(stops("PLACE:4"));

        ItineraryDto d = service.plan(ItineraryMock.request(3, null, TransportMode.WALK, List.of(), null), "k");

        assertThat(d.stops().get(0).arriveAt()).isEqualTo(START);
    }

    @Test
    void aDayEndsBy22_andTodayWithLessThanAnHourLeft_is400() {
        claudeAnswers(stops("PLACE:4", "PLACE:20", "PLACE:2", "PLACE:7", "PLACE:3", "PLACE:1"));
        // 14 nov., 19:40 la Arad → începe la 19:45, se termină cel târziu la 22:00
        service.setNowClock(Clock.fixed(Instant.parse("2026-11-14T17:40:00Z"), ZoneOffset.UTC));
        ItineraryDto d = service.plan(ItineraryMock.request(8, null, TransportMode.WALK, List.of(), null), "k");
        assertThat(d.stops()).isNotEmpty();
        assertThat(d.stops().get(d.stops().size() - 1).leaveAt()).isBeforeOrEqualTo(OffsetDateTime.parse("2026-11-14T22:00:00+02:00"));

        // 21:10 → ar începe la 21:15, mai puțin de o oră
        service.setNowClock(Clock.fixed(Instant.parse("2026-11-14T19:10:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> service.plan(ItineraryMock.request(3, null, TransportMode.WALK, List.of(), null), "k"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void claudesSummary_isKeptWhenAllStopsFit_elseOursDescribesTheStopsThatRemain() {
        claudeAnswers(stops("PLACE:4", "PLACE:1"));
        assertThat(service.plan(ItineraryMock.request(4, null, TransportMode.WALK, List.of(), null), "k").summary()).isEqualTo("Un tur scurt.");

        // 7 opriri în 3 h: nu încap toate, deci textul lui Claude ar putea pomeni opriri scoase din program
        claudeAnswers(stops("PLACE:4", "PLACE:20", "PLACE:2", "PLACE:7", "PLACE:3", "PLACE:1", "PLACE:30"));
        ItineraryDto d = service.plan(ItineraryMock.request(3, null, TransportMode.WALK, List.of(), null), "k");
        assertThat(d.stops().size()).isLessThan(7);
        assertThat(d.summary()).isNotEqualTo("Un tur scurt.").contains(String.valueOf(d.stops().size()));
    }
}

