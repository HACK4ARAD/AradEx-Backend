package aradexperience.service.events;

import aradexperience.enums.EventCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;

import java.time.OffsetDateTime;
import java.util.Set;

/** Filtrele din GET /events, deja validate de {@link EventService#query}. Seturile goale = fără filtru. */
public record EventQuery(
        OffsetDateTime from,
        OffsetDateTime toExclusive,
        Set<EventCategory> categories,
        LatLng near,
        Double radiusKm,
        Long organizerBusinessId,
        Set<OrganizerType> organizerTypes,
        Set<TicketType> ticketTypes,
        boolean hotOnly,
        String q,
        boolean byDistance,
        Set<ContentStatus> statuses,
        int page,
        int size) {

    public record LatLng(double lat, double lng) {}
}
