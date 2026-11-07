package aradexperience.dto.events;

import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** docs/API.md: EventDto = EventSummary + detaliile (aceleași câmpuri, în aceeași ordine, apoi cele în plus). */
public record EventDto(Long id, String title, EventCategory category, OffsetDateTime startAt, OffsetDateTime endAt,
                       String locationName, double lat, double lng, String imageUrl, TicketType ticketType,
                       BigDecimal priceFrom, boolean isHot, boolean isDemo, int participantsCount, String organizerName,
                       OrganizerType organizerType, Double distanceKm, Boolean saved, Double ratingAvg, int ratingCount,
                       String description, String address, Long placeId, Long organizerBusinessId, BigDecimal priceAdult,
                       BigDecimal priceChild, String externalUrl, Integer capacity, Integer spotsLeft, int savesCount,
                       ContentStatus status) {
}
