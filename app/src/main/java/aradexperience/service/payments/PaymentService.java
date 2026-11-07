package aradexperience.service.payments;

import aradexperience.dto.tickets.TicketRequest;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.security.CurrentUser;
import aradexperience.service.tickets.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * T25 · plata biletelor cu cardul prin **Stripe în mod test** (fără bani reali; carduri de test, ex. 4242 4242 4242 4242).
 * 1. `checkout`: verifică comanda (aceleași reguli ca biletele) și creează un PaymentIntent cu comanda în `metadata`;
 *    aplicația primește `clientSecret` și arată formularul de card Stripe (Payment Element).
 * 2. `confirm`: după plată, aplicația cere biletul; Stripe e întrebat direct dacă PaymentIntent-ul e `succeeded`.
 * 3. `webhook`: Stripe anunță singur plata reușită (dacă aplicația s-a închis între timp); biletul se emite o singură dată.
 * Dacă locurile s-au epuizat între plată și bilet, plata se rambursează automat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    /** marcajul din `metadata`: doar PaymentIntent-urile create de noi dau bilete */
    static final String SOURCE = "aradexperience";
    static final String NOT_COMPLETED = "Payment not completed";
    static final String NO_PAYMENT_NEEDED = "No card payment needed for this order";

    private final StripeProps props;
    private final StripeClient stripe;
    private final TicketService tickets;
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record Config(boolean enabled, String publishableKey, String currency) {}

    public record Checkout(String paymentIntentId, String clientSecret, BigDecimal amount, String currency) {}

    @PostConstruct
    void announce() {
        if (props.liveKeyGiven()) log.warn("Stripe live keys are ignored: only test keys (sk_test_/pk_test_) are accepted");
        else if (props.enabled()) log.info("Stripe card payments on, test mode{}", props.webhookConfigured() ? " (webhook on)" : "");
    }

    public Config config() {
        return new Config(props.enabled(), props.enabled() ? props.publishableKey().trim() : null, props.currencyOrDefault());
    }

    /** Pasul 1: PaymentIntent-ul pentru comandă; suma vine din prețurile evenimentului, nu de la aplicație. */
    public Checkout checkout(long eventId, TicketRequest req, CurrentUser user) {
        requireEnabled();
        TicketService.Quote q = tickets.quote(eventId, req, user);
        if (q.total().signum() <= 0) throw new BadRequestException(NO_PAYMENT_NEEDED);
        long amount = q.total().movePointRight(2).longValueExact();
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("source", SOURCE);
        meta.put("eventId", Long.toString(eventId));
        meta.put("userId", Long.toString(user.id()));
        meta.put("qtyAdult", Integer.toString(req.qtyAdult()));
        meta.put("qtyChild", Integer.toString(req.qtyChild()));
        if (req.email() != null && !req.email().isBlank()) meta.put("email", req.email().trim());
        String description = "AradExperience · " + q.event().getTitle() + " · " + (req.qtyAdult() + req.qtyChild()) + " bilet(e)";
        StripeClient.PaymentIntent pi = stripe.createPaymentIntent(amount, props.currencyOrDefault(), description, meta,
                "checkout-" + user.id() + "-" + UUID.randomUUID());
        log.info("Payment {} started for event {} ({} {})", pi.id(), eventId, q.total(), props.currencyOrDefault());
        return new Checkout(pi.id(), pi.clientSecret(), q.total(), props.currencyOrDefault());
    }

    /** Pasul 2: biletul, după ce Stripe confirmă plata. Idempotent: a doua cerere (sau webhook-ul) primește același bilet. */
    public TicketService.TicketView confirm(String paymentIntentId, String email, CurrentUser user) {
        requireEnabled();
        StripeClient.PaymentIntent pi = stripe.retrieve(paymentIntentId);
        if (!SOURCE.equals(pi.metadata().get("source"))) throw NotFoundException.of("Payment", paymentIntentId);
        if (!String.valueOf(user.id()).equals(pi.metadata().get("userId")) && !user.isAdmin()) {
            throw new ForbiddenException("This payment belongs to another account");
        }
        if (!"succeeded".equals(pi.status())) throw new ConflictException(NOT_COMPLETED);
        return fulfil(pi, email);
    }

    /** Webhook-ul Stripe: semnătura e obligatorie; ce nu e `payment_intent.succeeded` al nostru se ignoră (200). */
    public void webhook(String payload, String signature) {
        if (!props.webhookConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook not configured");
        if (!StripeSignature.valid(payload, signature, props.webhookSecret(), Instant.now(clock))) {
            throw new BadRequestException("Invalid Stripe signature");
        }
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (java.io.IOException e) {
            throw new BadRequestException("Invalid Stripe event");
        }
        if (!"payment_intent.succeeded".equals(event.path("type").asText())) return;
        StripeClient.PaymentIntent pi = StripeClient.PaymentIntent.of(event.path("data").path("object"));
        if (!SOURCE.equals(pi.metadata().get("source"))) return;
        try {
            fulfil(pi, null);
        } catch (ConflictException | NotFoundException e) {
            log.warn("Webhook: payment {} not turned into a ticket: {}", pi.id(), e.getMessage()); // deja rambursată în fulfil
        }
    }

    private TicketService.TicketView fulfil(StripeClient.PaymentIntent pi, String email) {
        Map<String, String> m = pi.metadata();
        String mail = email != null && !email.isBlank() ? email.trim() : m.get("email");
        TicketRequest req = new TicketRequest(intOf(m, "qtyAdult"), intOf(m, "qtyChild"), mail);
        BigDecimal paid = BigDecimal.valueOf(pi.amount()).movePointLeft(2);
        try {
            return tickets.issuePaid(longOf(m, "eventId"), req, longOf(m, "userId"), pi.id(), paid);
        } catch (ConflictException | NotFoundException e) {
            // locuri epuizate sau eveniment șters între plată și bilet: banii (de test) se dau înapoi
            stripe.refund(pi.id());
            throw new ConflictException(e.getMessage() + " — payment refunded");
        }
    }

    private void requireEnabled() {
        if (!props.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, StripeClient.UNAVAILABLE);
    }

    private static long longOf(Map<String, String> m, String k) {
        try { return Long.parseLong(m.get(k)); } catch (NumberFormatException | NullPointerException e) { throw new BadRequestException("Invalid payment metadata"); }
    }

    private static int intOf(Map<String, String> m, String k) {
        return (int) longOf(m, k);
    }
}
