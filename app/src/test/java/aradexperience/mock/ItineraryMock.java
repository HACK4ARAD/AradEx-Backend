package aradexperience.mock;

import aradexperience.dto.itineraries.GeoPoint;
import aradexperience.dto.itineraries.ItineraryDto;
import aradexperience.dto.itineraries.ItineraryRequest;
import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.entity.itineraries.Itinerary;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.places.Place;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import aradexperience.enums.ItineraryStopKind;
import aradexperience.enums.OfferCategory;
import aradexperience.enums.PlaceType;
import aradexperience.enums.TransportMode;
import aradexperience.repository.places.GuidePointRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** Date de test pentru planificator (T13): obiectivele reale din centru (coordonatele din V3_2), o cramă din județ, un eveniment, o ofertă. */
public final class ItineraryMock {

    /** Sâmbătă, 14 nov. 2026. */
    public static final LocalDate DATE = LocalDate.of(2026, 11, 14);

    private ItineraryMock() {}

    public static Place place(long id, String name, PlaceType type, double lat, double lng, int visit, String tags) {
        Place p = PlaceMock.place(id, name, type, lat, lng, tags);
        p.setVisitMinutes(visit);
        return p;
    }

    /** Opt locuri: șase în centru (patru cu ghid), o cafenea, crama de la Miniș (județ, cu indicație de parcare). */
    public static List<Place> places() {
        Place cetatea = place(1, "Cetatea Aradului", PlaceType.ATTRACTION, 46.1709, 21.3326, 60, "istorie,familie,foto,in-aer-liber");
        cetatea.setExteriorOnly(true);
        cetatea.setParkingHint("Piața 13 Martiri (Subcetate)");
        Place cultural = place(2, "Palatul Cultural", PlaceType.MUSEUM, 46.1742, 21.3207, 75, "muzee,istorie,arta,familie");
        cultural.setParkingHint("Bd. Dragalina / Parcul Copiilor");
        Place teatrul = place(3, "Teatrul Clasic „Ioan Slavici”", PlaceType.VENUE, 46.1711, 21.3158, 20, "cultura,istorie,arhitectura");
        Place primaria = place(4, "Palatul Administrativ (Primăria)", PlaceType.ATTRACTION, 46.1754, 21.3193, 15, "arhitectura,istorie,foto");
        primaria.setParkingHint("Parcările cu plată de pe Bd. Revoluției");
        Place anton = place(7, "Catedrala Romano-Catolică „Sfântul Anton de Padova”", PlaceType.ATTRACTION, 46.1726, 21.3154, 20, "istorie,spiritual");
        Place neuman = place(20, "Palatul Neuman", PlaceType.ATTRACTION, 46.1754, 21.3172, 10, "arhitectura,istorie,foto");
        Place cafe = place(30, "Cafeneaua de pe Bulevard", PlaceType.CAFE, 46.1760, 21.3180, 30, "cafea,gastronomie");
        Place minis = place(11, "Podgoria Miniș–Măderat și Muzeul Viei și Vinului", PlaceType.WINERY, 46.1378, 21.6048, 90, "vin,judet,istorie");
        minis.setParkingHint("Parcarea muzeului din Miniș");
        return new ArrayList<>(List.of(cetatea, cultural, teatrul, primaria, anton, neuman, cafe, minis));
    }

    /** Treizeci de locuri sintetice pe o rază de ~2 km de centru, câte 60 de minute — destule pentru două zile pe jos. */
    public static List<Place> manyPlaces() {
        List<Place> out = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double a = i * 0.7;
            double r = 0.004 + 0.0006 * i;
            out.add(place(100 + i, "Obiectivul " + i, PlaceType.ATTRACTION, 46.1754 + r * Math.sin(a), 21.3193 + r * Math.cos(a), 60, "istorie"));
        }
        return out;
    }

    public static GuidePointRepository.GuideStats guide(long placeId) {
        return new GuidePointRepository.GuideStats() {
            public Long getPlaceId() { return placeId; }
            public Long getCount() { return 4L; }
            public Long getSeconds() { return 240L; }
        };
    }

    /** Ghid audio la Cetate, Palatul Cultural, Primărie, Sf. Anton. */
    public static List<GuidePointRepository.GuideStats> guides() {
        return List.of(guide(1), guide(2), guide(4), guide(7));
    }

    public static Event event(long id, String title, String start, String end) {
        Event e = EventMock.event(id, title, EventCategory.CONCERT, start, aradexperience.enums.TicketType.FREE);
        e.setEndAt(end == null ? null : OffsetDateTime.parse(end));
        e.setPlaceId(2L);
        return e;
    }

    public static Business cafeBusiness() {
        Business b = new Business();
        b.setId(5L);
        b.setName("Cafeneaua din Pasaj");
        b.setLat(46.1748);
        b.setLng(21.3185);
        return b;
    }

    public static Offer coffeeOffer() {
        Offer o = new Offer();
        o.setId(42L);
        o.setBusinessId(5L);
        o.setTitle("10% la orice cafea");
        o.setDiscountLabel("-10%");
        o.setCategory(OfferCategory.CAFE);
        o.setStatus(ContentStatus.PUBLISHED);
        o.setValidFrom(DATE.minusDays(10));
        return o;
    }

    public static ItineraryRequest request(Integer hours, Integer days, TransportMode mode, List<String> interests, String freeText) {
        return new ItineraryRequest(DATE, "10:00", hours, days, mode, interests, freeText, null, "ro");
    }

    public static ItineraryStop stop(int order, long refId, String name, String arrive, String leave) {
        return new ItineraryStop(order, 1, ItineraryStopKind.PLACE, refId, name, "Obiectiv turistic", 46.1754, 21.3193,
                OffsetDateTime.parse(arrive), OffsetDateTime.parse(leave), 15, 0, 0.0, TransportMode.WALK, "O notă", null, true, null);
    }

    public static ItineraryDto dto(Long id, String shareCode) {
        return new ItineraryDto(id, shareCode, "Jumătate de zi prin centrul istoric", "Două opriri pe jos.", "Jumătate de zi, pe jos, istorie",
                DATE, 1, TransportMode.WALK, "10:00", 95, 1.6, true,
                List.of(stop(1, 4, "Palatul Administrativ (Primăria)", "2026-11-14T10:00:00+02:00", "2026-11-14T10:15:00+02:00"),
                        stop(2, 1, "Cetatea Aradului", "2026-11-14T10:35:00+02:00", "2026-11-14T11:35:00+02:00")),
                id == null ? null : OffsetDateTime.parse("2026-11-07T10:00:00+02:00"));
    }

    public static Itinerary entity(long id, String shareCode, Long userId) {
        Itinerary i = aradexperience.mapper.itineraries.ItineraryMapper.toEntity(dto(null, null));
        i.setId(id);
        i.setShareCode(shareCode);
        i.setUserId(userId);
        i.setCreatedAt(OffsetDateTime.parse("2026-11-07T08:00:00Z"));
        i.setTotalKm(new BigDecimal("1.6"));
        return i;
    }
}
