package aradexperience.mapper.tickets;

import aradexperience.dto.tickets.TicketDto;
import aradexperience.dto.tickets.TicketPaymentDto;
import aradexperience.dto.tickets.TicketRequest;
import aradexperience.enums.PaymentMode;
import aradexperience.service.tickets.TicketService;
import aradexperience.entity.events.Event;
import aradexperience.entity.tickets.Ticket;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Locale;

/** Ticket (+ evenimentul lui) ↔ TicketDto. */
@UtilityClass
public class TicketMapper {

    public static TicketDto toDto(Ticket t, Event e) {
        return new TicketDto(t.getCode(), e.getId(), e.getTitle(), e.getStartAt(), e.getEndAt(), e.getLocationName(), e.getAddress(),
                e.getLat(), e.getLng(), e.getImageUrl(), e.getTicketType(), t.getQtyAdult(), t.getQtyChild(), num(e.getPriceAdult()),
                num(e.getPriceChild()), t.getTotal().doubleValue(), t.getStatus(), t.getEmail(), t.getCreatedAt(), t.getUsedAt());
    }

    public static Ticket toEntity(TicketRequest req, long eventId, long userId, BigDecimal total) {
        Ticket t = new Ticket();
        t.setEventId(eventId);
        t.setUserId(userId);
        t.setQtyAdult(req.qtyAdult());
        t.setQtyChild(req.qtyChild());
        t.setTotal(total);
        // T21: conturile cu telefon pot lua bilete fără email
        t.setEmail(req.email() == null || req.email().isBlank() ? null : req.email().trim().toLowerCase(Locale.ROOT));
        return t;
    }

    /** STRIPE: PaymentIntent-ul de test (clientSecret pentru Stripe.js); DEMO: fără Stripe, doar totalul. */
    public static TicketPaymentDto toPaymentDto(TicketService.Payment p) {
        if (p.intent() == null) return new TicketPaymentDto(PaymentMode.DEMO, p.quote().total(), null, null, null, null);
        return new TicketPaymentDto(PaymentMode.STRIPE, p.quote().total(), p.currency(), p.intent().id(), p.intent().clientSecret(), p.publishableKey());
    }

    private static Double num(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
