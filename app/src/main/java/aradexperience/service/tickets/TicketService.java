package aradexperience.service.tickets;

import aradexperience.dto.tickets.TicketRequest;
import aradexperience.entity.events.Event;
import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.enums.TicketStatus;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.tickets.TicketRepository;
import aradexperience.security.CurrentUser;
import aradexperience.service.events.EventService;
import aradexperience.service.payments.StripeClient;
import aradexperience.service.payments.TicketPayments;
import aradexperience.util.Codes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.mapper.tickets.TicketMapper.toEntity;

/**
 * Bilete (T07): cumpărare IN_APP cu plată demonstrativă, înscriere gratuită (FREE cu locuri), biletele mele, folosirea la intrare,
 * fișierul .ics. Locurile se verifică pe rândul evenimentului blocat, ca două comenzi simultane să nu treacă peste capacitate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketService {

    static final int MAX_CODE_ATTEMPTS = 5;
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final EventRepository events;
    private final TicketRepository tickets;
    private final BusinessRepository businesses;
    private final TicketPayments payments;

    private Clock clock = Clock.system(EventService.ZONE);

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Biletul cu evenimentul lui, pentru mapper. */
    public record TicketView(Ticket ticket, Event event) {}

    /** Comanda verificată: evenimentul (blocat), câte locuri și totalul calculat de server. */
    public record Quote(Event event, int qty, BigDecimal total) {}

    /**
     * Plata unei comenzi (Stripe în modul de test): aceleași verificări ca la cumpărare (eveniment, cantități, locuri), apoi un
     * PaymentIntent pentru totalul calculat aici. Fără chei Stripe, la evenimente gratuite sau fără preț → DEMO.
     */
    public record Payment(Quote quote, StripeClient.Intent intent, String publishableKey, String currency) {}

    @Transactional
    public Payment preparePayment(long eventId, TicketRequest req, CurrentUser user) {
        Quote q = quote(published(eventId), req, user);
        if (!paid(q)) return new Payment(q, null, null, null);
        return new Payment(q, payments.prepare(q.event(), req.qtyAdult(), req.qtyChild(), q.total(), user), payments.publishableKey(), payments.currency());
    }

    @Transactional
    public TicketView buy(long eventId, TicketRequest req, CurrentUser user) {
        String pi = req.paymentIntentId() == null || req.paymentIntentId().isBlank() ? null : req.paymentIntentId().trim();
        Event e = published(eventId);
        // T25: verificarea după blocarea evenimentului — webhook-ul Stripe poate să fi emis deja biletul pentru aceeași plată
        if (pi != null && payments.enabled()) {
            var issued = tickets.findByPaymentIntentId(pi);
            if (issued.isPresent()) {
                if (issued.get().getUserId().equals(user.id())) return new TicketView(issued.get(), e);
                throw new ConflictException("Payment already used");
            }
        }
        Quote q;
        try {
            q = quote(e, req, user);
        } catch (ApiException ex) {
            // plătit, dar biletul nu se mai poate emite (ex. locurile s-au terminat între timp): banii de test se întorc
            if (pi != null && payments.enabled()) payments.refund(pi, user);
            throw ex;
        }
        boolean stripePaid = paid(q);
        if (stripePaid) payments.verify(pi, e, req.qtyAdult(), req.qtyChild(), q.total(), user);

        Ticket entity = toEntity(req, eventId, user.id(), q.total());
        if (stripePaid) entity.setPaymentIntentId(pi);
        Ticket t = saveWithUniqueCode(entity);
        e.setParticipantsCount(e.getParticipantsCount() + q.qty());
        log.info("Ticket {} for event {} ({} x{})", t.getCode(), eventId, q.qty(), stripePaid ? ", Stripe test payment" : "");
        return new TicketView(t, e);
    }

    /**
     * T25 · webhook-ul Stripe: biletul pentru o plată reușită, dacă aplicația nu l-a cerut deja (s-a închis după plată). Comanda
     * vine din `metadata` PaymentIntent-ului (eveniment, utilizator, cantități) și trece prin aceleași verificări ca `buy`
     * (sumă, locuri); un PaymentIntent dă un singur bilet. Locuri epuizate între timp → plata se rambursează, fără bilet.
     */
    @Transactional
    public java.util.Optional<TicketView> issueFromPayment(StripeClient.Intent pi) {
        var m = pi.metadata();
        if (!payments.enabled() || !"aradexperience".equals(m.get("app")) || !"succeeded".equals(pi.status())) return java.util.Optional.empty();
        if (tickets.existsByPaymentIntentId(pi.id())) return java.util.Optional.empty();
        long eventId;
        int qtyAdult;
        int qtyChild;
        CurrentUser owner;
        try {
            eventId = Long.parseLong(m.get("eventId"));
            qtyAdult = Integer.parseInt(m.get("qtyAdult"));
            qtyChild = Integer.parseInt(m.get("qtyChild"));
            owner = new CurrentUser(Long.parseLong(m.get("userId")), null, null);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("Webhook: PaymentIntent {} has no usable order metadata", pi.id());
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(buy(eventId, new TicketRequest(qtyAdult, qtyChild, null, pi.id()), owner));
        } catch (ConflictException | NotFoundException | BadRequestException e) {
            log.warn("Webhook: payment {} not turned into a ticket: {}", pi.id(), e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /** Biletele cu preț se plătesc prin Stripe doar când sunt chei de test; altfel (sau la 0 lei) plata e demonstrativă. */
    private boolean paid(Quote q) {
        return payments.enabled() && q.event().getTicketType() == TicketType.IN_APP && q.total().signum() > 0;
    }

    private Event published(long eventId) {
        return events.findForUpdate(eventId).filter(ev -> ev.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> NotFoundException.of("Event", eventId));
    }

    private Quote quote(Event e, TicketRequest req, CurrentUser user) {
        long eventId = e.getId();
        if (e.effectiveEnd().isBefore(OffsetDateTime.now(clock))) throw new BadRequestException("The event has ended");
        int qty = req.qtyAdult() + req.qtyChild();
        if (qty < 1) throw new BadRequestException("Choose at least one ticket");

        BigDecimal total = switch (e.getTicketType()) {
            case EXTERNAL -> throw new BadRequestException("Tickets for this event are sold on the organizer's site");
            case FREE -> {
                if (e.getCapacity() == null) throw new BadRequestException("Free entry, no registration needed");
                if (qty != 1 || req.qtyChild() != 0) throw new BadRequestException("A free registration is for one person");
                if (tickets.existsByUserIdAndEventIdAndStatusNot(user.id(), eventId, TicketStatus.CANCELLED)) {
                    throw new ConflictException("You are already registered for this event");
                }
                yield BigDecimal.ZERO;
            }
            case IN_APP -> {
                if (req.qtyChild() > 0 && e.getPriceChild() == null) throw new BadRequestException("This event has no child tickets");
                yield price(e.getPriceAdult()).multiply(BigDecimal.valueOf(req.qtyAdult()))
                        .add(price(e.getPriceChild()).multiply(BigDecimal.valueOf(req.qtyChild())));
            }
        };
        if (e.getCapacity() != null) {
            int left = Math.max(0, e.getCapacity() - e.getParticipantsCount());
            if (qty > left) throw new ConflictException(left == 0 ? "Sold out" : "Only " + left + " spots left");
        }
        return new Quote(e, qty, total);
    }

    /** upcoming = true: cele care nu s-au terminat, după dată; false: cele trecute, cele recente primele; null: toate. */
    public List<TicketView> mine(CurrentUser user, Boolean upcoming) {
        List<Ticket> mine = tickets.findByUserIdAndStatusNot(user.id(), TicketStatus.CANCELLED);
        if (mine.isEmpty()) return List.of();
        Map<Long, Event> byId = events.findAllById(mine.stream().map(Ticket::getEventId).distinct().toList()).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        Comparator<TicketView> byStart = Comparator.comparing(v -> v.event().getStartAt());
        return mine.stream().map(t -> new TicketView(t, byId.get(t.getEventId())))
                .filter(v -> v.event() != null)
                .filter(v -> upcoming == null || upcoming == !v.event().effectiveEnd().isBefore(now))
                .sorted(Boolean.FALSE.equals(upcoming) ? byStart.reversed() : byStart)
                .toList();
    }

    public TicketView get(String code, CurrentUser user) {
        TicketView v = load(code);
        if (!v.ticket().getUserId().equals(user.id()) && !isOrganizer(v.event(), user)) throw new ForbiddenException("Not your ticket");
        return v;
    }

    /** La intrare: organizatorul (creatorul evenimentului sau firma lui) ori Adminul. A doua scanare = 409. */
    @Transactional
    public TicketView use(String code, CurrentUser user) {
        TicketView v = load(code);
        if (!isOrganizer(v.event(), user)) throw new ForbiddenException("Only the organizer can validate tickets");
        if (v.ticket().getStatus() == TicketStatus.USED) throw new ConflictException("Ticket already used");
        if (v.ticket().getStatus() == TicketStatus.CANCELLED) throw new ConflictException("Ticket cancelled");
        v.ticket().setStatus(TicketStatus.USED);
        v.ticket().setUsedAt(OffsetDateTime.now(clock));
        return v;
    }

    /** iCalendar (RFC 5545) cu ora în UTC; fără `endAt` evenimentul durează 2 ore. */
    public String ics(long eventId) {
        Event e = events.findById(eventId).filter(ev -> ev.getStatus() == ContentStatus.PUBLISHED).orElseThrow(() -> NotFoundException.of("Event", eventId));
        OffsetDateTime end = e.getEndAt() != null ? e.getEndAt() : e.getStartAt().plusHours(2);
        String location = e.getAddress() == null ? e.getLocationName() : e.getLocationName() + ", " + e.getAddress();
        return String.join("\r\n",
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//AradExperience//Hack4Arad 2026//RO", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:event-" + e.getId() + "@aradexperience.ro",
                "DTSTAMP:" + utc(OffsetDateTime.now(clock)),
                "DTSTART:" + utc(e.getStartAt()),
                "DTEND:" + utc(end),
                "SUMMARY:" + escape(e.getTitle()),
                "LOCATION:" + escape(location),
                "DESCRIPTION:" + escape(e.getDescription()),
                "GEO:" + e.getLat() + ";" + e.getLng(),
                e.getExternalUrl() == null ? "X-ORGANIZER-NAME:" + escape(e.getOrganizerName()) : "URL:" + e.getExternalUrl(),
                "END:VEVENT", "END:VCALENDAR", "");
    }

    private TicketView load(String code) {
        Ticket t = tickets.findByCode(code.trim().toUpperCase()).orElseThrow(() -> NotFoundException.of("Ticket", code));
        Event e = events.findById(t.getEventId()).orElseThrow(() -> NotFoundException.of("Event", t.getEventId()));
        return new TicketView(t, e);
    }

    private boolean isOrganizer(Event e, CurrentUser user) {
        if (user.isAdmin() || user.id().equals(e.getCreatedBy())) return true;
        return user.is(Role.BUSINESS) && e.getOrganizerBusinessId() != null
                && businesses.findByOwnerUserId(user.id()).map(b -> b.getId().equals(e.getOrganizerBusinessId())).orElse(false);
    }

    /** Codul are 6 caractere aleatoare (32^6): coliziunile sunt foarte rare, dar dacă apar se reîncearcă. */
    private Ticket saveWithUniqueCode(Ticket t) {
        for (int attempt = 1; ; attempt++) {
            t.setCode(Codes.generate("AX-T" + t.getEventId()));
            try {
                return tickets.saveAndFlush(t);
            } catch (DataIntegrityViolationException duplicate) {
                if (attempt >= MAX_CODE_ATTEMPTS) throw new ConflictException("Could not generate a ticket code, try again");
            }
        }
    }

    private static BigDecimal price(BigDecimal p) {
        return p == null ? BigDecimal.ZERO : p;
    }

    private static String utc(OffsetDateTime t) {
        return t.withOffsetSameInstant(ZoneOffset.UTC).format(ICS_TIME);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }
}
