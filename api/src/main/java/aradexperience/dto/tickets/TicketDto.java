package aradexperience.dto.tickets;

import aradexperience.enums.TicketStatus;
import aradexperience.enums.TicketType;

import java.time.OffsetDateTime;

/**
 * docs/API.md: TicketDto — S6, S9, S10, „Contul meu › Bilete”. Pe lângă câmpurile de bază are tot ce trebuie ca biletul să se
 * afișeze offline (loc, adresă, coordonate pentru „Indicații”, prețurile unitare pentru rânduri, poza).
 */
public record TicketDto(
        String code,
        Long eventId,
        String eventTitle,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        String locationName,
        String address,
        double lat,
        double lng,
        String imageUrl,
        TicketType ticketType,
        int qtyAdult,
        int qtyChild,
        Double priceAdult,
        Double priceChild,
        double total,
        TicketStatus status,
        String email,
        OffsetDateTime createdAt,
        OffsetDateTime usedAt) {
}
