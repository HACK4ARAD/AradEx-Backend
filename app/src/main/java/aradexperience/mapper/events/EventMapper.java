package aradexperience.mapper.events;

import aradexperience.config.AppProps;
import aradexperience.dto.events.EventCreate;
import aradexperience.dto.events.EventDto;
import aradexperience.dto.events.EventSummary;
import aradexperience.entity.events.Event;
import aradexperience.service.events.EventView;
import lombok.experimental.UtilityClass;

/**
 * Event ↔ DTO-urile din `dto/events`. Introdus de T09; T06 îl refolosește (și adaugă `distanceKm`/`saved` ca parametri).
 * Pragurile „Popular” (`app.events.*`) vin ca parametru — mapper-ul nu are dependențe.
 */
@UtilityClass
public class EventMapper {

    public static boolean isHot(Event e, AppProps.Events hot) {
        return e.getParticipantsCount() >= hot.hotParticipants() || e.getSavesCount() >= hot.hotSaves();
    }

    public static EventSummary toSummary(Event e, AppProps.Events hot) {
        return new EventSummary(e.getId(), e.getTitle(), e.getCategory(), e.getStartAt(), e.getEndAt(), e.getLocationName(),
                e.getLat(), e.getLng(), e.getImageUrl(), e.getTicketType(), e.getPriceFrom(), isHot(e, hot), e.isDemo(),
                e.getParticipantsCount(), e.getOrganizerName(), e.getOrganizerType(), null, null, null, 0, e.getCapacity(), spotsLeft(e));
    }

    /** Locurile rămase (null = fără limită). */
    public static Integer spotsLeft(Event e) {
        return e.getCapacity() == null ? null : Math.max(0, e.getCapacity() - e.getParticipantsCount());
    }

    public static EventDto toDto(Event e, AppProps.Events hot) {
        Integer spotsLeft = e.getCapacity() == null ? null : Math.max(0, e.getCapacity() - e.getParticipantsCount());
        return new EventDto(e.getId(), e.getTitle(), e.getCategory(), e.getStartAt(), e.getEndAt(), e.getLocationName(),
                e.getLat(), e.getLng(), e.getImageUrl(), e.getTicketType(), e.getPriceFrom(), isHot(e, hot), e.isDemo(),
                e.getParticipantsCount(), e.getOrganizerName(), e.getOrganizerType(), null, null, null, 0,
                e.getDescription(), e.getAddress(), e.getPlaceId(), e.getOrganizerBusinessId(), e.getPriceAdult(),
                e.getPriceChild(), e.getExternalUrl(), e.getCapacity(), spotsLeft, e.getSavesCount(), e.getStatus(), null);
    }

    /** T06: cardul cu câmpurile calculate pentru utilizatorul curent (distanță, salvat, popular). */
    public static EventSummary toSummary(EventView v) {
        Event e = v.event();
        return new EventSummary(e.getId(), e.getTitle(), e.getCategory(), e.getStartAt(), e.getEndAt(), e.getLocationName(),
                e.getLat(), e.getLng(), e.getImageUrl(), e.getTicketType(), e.getPriceFrom(), v.hot(), e.isDemo(),
                e.getParticipantsCount(), e.getOrganizerName(), e.getOrganizerType(), v.distanceKm(), v.saved(), null, 0,
                e.getCapacity(), spotsLeft(e));
    }

    /** T06: detaliul cu câmpurile calculate + creditul pozei. */
    public static EventDto toDto(EventView v) {
        Event e = v.event();
        Integer spotsLeft = e.getCapacity() == null ? null : Math.max(0, e.getCapacity() - e.getParticipantsCount());
        return new EventDto(e.getId(), e.getTitle(), e.getCategory(), e.getStartAt(), e.getEndAt(), e.getLocationName(),
                e.getLat(), e.getLng(), e.getImageUrl(), e.getTicketType(), e.getPriceFrom(), v.hot(), e.isDemo(),
                e.getParticipantsCount(), e.getOrganizerName(), e.getOrganizerType(), v.distanceKm(), v.saved(), null, 0,
                e.getDescription(), e.getAddress(), e.getPlaceId(), e.getOrganizerBusinessId(), e.getPriceAdult(),
                e.getPriceChild(), e.getExternalUrl(), e.getCapacity(), spotsLeft, e.getSavesCount(), e.getStatus(), v.imageCredit());
    }

    /**
     * Copiază câmpurile editabile din cerere (titlu, descriere, categorie, timp, bilete, poză). Locația, organizatorul,
     * `priceFrom` și starea le stabilește service-ul.
     */
    public static void apply(EventCreate req, Event e) {
        e.setTitle(req.title().trim());
        e.setDescription(req.description().trim());
        e.setCategory(req.category());
        e.setPlaceId(req.placeId());
        e.setStartAt(req.startAt());
        e.setEndAt(req.endAt());
        e.setTicketType(req.ticketType());
        e.setPriceAdult(req.priceAdult());
        e.setPriceChild(req.priceChild());
        e.setExternalUrl(blankToNull(req.externalUrl()));
        e.setCapacity(req.capacity());
        e.setImageUrl(blankToNull(req.imageUrl()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
