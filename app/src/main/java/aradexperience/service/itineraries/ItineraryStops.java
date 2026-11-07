package aradexperience.service.itineraries;

import aradexperience.dto.itineraries.ItineraryStop;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.places.Place;
import aradexperience.exceptions.BadRequestException;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.repository.places.GuidePointRepository;
import aradexperience.repository.places.PlaceRepository;
import aradexperience.service.ai.ItineraryFallback;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * T13 · la salvare, opririle se refac din baza de date după `kind` + `refId`: numele, locul, coordonatele, poza, ghidul și
 * parcarea vin din DB, nu din cerere (un itinerariu partajat nu poate afișa texte, poze sau locuri inventate). Din cerere rămân
 * doar orele, duratele, drumurile și nota (scurtată); subtitlul unui loc e tipul lui, în ro sau en. O oprire necunoscută → 400.
 */
@Component
@RequiredArgsConstructor
public class ItineraryStops {

    static final int MAX_NOTE = 300;
    static final int MAX_SUBTITLE = 80;

    private final PlaceRepository places;
    private final EventRepository events;
    private final OfferRepository offers;
    private final BusinessRepository businesses;
    private final GuidePointRepository guide;
    private final ItineraryFallback labels;

    public List<ItineraryStop> canonical(List<ItineraryStop> in) {
        Map<Long, Place> ps = byId(places.findAllById(ids(in, "PLACE")), Place::getId);
        Map<Long, Event> es = byId(events.findAllById(ids(in, "EVENT")), Event::getId);
        Map<Long, Offer> os = byId(offers.findAllById(ids(in, "OFFER")), Offer::getId);
        Map<Long, Business> bs = byId(businesses.findAllById(os.values().stream().map(Offer::getBusinessId).collect(Collectors.toSet())), Business::getId);
        Set<Long> guided = guide.stats().stream().map(GuidePointRepository.GuideStats::getPlaceId).collect(Collectors.toSet());

        return in.stream().map(s -> switch (s.kind()) {
            case PLACE -> {
                Place p = need(ps.get(s.refId()), s);
                yield copy(s, p.getName(), placeSubtitle(s.subtitle(), p), p.getLat(), p.getLng(), p.getImageUrl(), guided.contains(p.getId()),
                        s.parkingHint() == null ? null : clip(p.getParkingHint(), 500));
            }
            case EVENT -> {
                Event e = need(es.get(s.refId()), s);
                String venue = e.getLocationName() != null && !e.getLocationName().isBlank() ? e.getLocationName() : e.getOrganizerName();
                yield copy(s, e.getTitle(), clip(venue, MAX_SUBTITLE), e.getLat(), e.getLng(), e.getImageUrl(),
                        e.getPlaceId() != null && guided.contains(e.getPlaceId()), null);
            }
            case OFFER -> {
                Offer o = need(os.get(s.refId()), s);
                Business b = need(bs.get(o.getBusinessId()), s);
                if (b.getLat() == null || b.getLng() == null) throw new BadRequestException("Unknown stop OFFER " + s.refId());
                yield copy(s, o.getTitle(), clip(b.getName(), MAX_SUBTITLE), b.getLat(), b.getLng(),
                        o.getImageUrl() != null ? o.getImageUrl() : b.getImageUrl(), false, null);
            }
        }).toList();
    }

    /** Tipul locului din DB, în limba în care l-a scris planificatorul (en dacă cererea are eticheta engleză, altfel ro). */
    private String placeSubtitle(String requested, Place p) {
        String en = labels.typeLabel("en", p.getType());
        return en != null && en.equals(requested) ? en : labels.typeLabel("ro", p.getType());
    }

    private static ItineraryStop copy(ItineraryStop s, String name, String subtitle, double lat, double lng, String image, boolean guide, String parking) {
        return new ItineraryStop(s.order(), s.day(), s.kind(), s.refId(), clip(name, 200), subtitle, lat, lng, s.arriveAt(), s.leaveAt(),
                s.durationMin(), s.travelMinutesFromPrev(), s.travelKmFromPrev(), s.travelMode(), clip(s.note(), MAX_NOTE), parking, guide, image);
    }

    private static Set<Long> ids(List<ItineraryStop> in, String kind) {
        return in.stream().filter(s -> s.kind().name().equals(kind)).map(ItineraryStop::refId).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a));
    }

    private static <T> T need(T found, ItineraryStop s) {
        if (found == null) throw new BadRequestException("Unknown stop " + s.kind() + " " + s.refId());
        return found;
    }

    static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max).strip();
    }
}
