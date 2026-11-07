package aradexperience.dto.events;

import aradexperience.enums.EventCategory;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * docs/API.md: EventSummary (cardul din liste). Introdus de T09 (evenimentele de pe pagina firmei); T06 îl refolosește în `GET /events`.
 * `distanceKm`, `saved`, `ratingAvg` sunt null când nu se cer / nu există încă (T06, T07).
 */
public record EventSummary(Long id, String title, EventCategory category, OffsetDateTime startAt, OffsetDateTime endAt,
                           String locationName, double lat, double lng, String imageUrl, TicketType ticketType,
                           BigDecimal priceFrom, boolean isHot, boolean isDemo, int participantsCount, String organizerName,
                           OrganizerType organizerType, Double distanceKm, Boolean saved, Double ratingAvg, int ratingCount) {
}
