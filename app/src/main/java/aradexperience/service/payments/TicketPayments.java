package aradexperience.service.payments;

import aradexperience.entity.events.Event;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.security.CurrentUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Plata biletelor IN_APP cu Stripe în modul de test. Fluxul: `prepare` creează un PaymentIntent pentru totalul calculat de
 * server (cu evenimentul, utilizatorul și cantitățile în metadata) → aplicația confirmă cardul cu Stripe.js → `verify` citește
 * PaymentIntent-ul de la Stripe și acceptă biletul doar dacă plata a reușit, pentru exact comanda asta. Fără chei de test,
 * `enabled()` e false și biletele se emit ca până acum (plată demonstrativă).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketPayments {

    static final String PAYMENT_REQUIRED = "Payment required";
    static final String PAYMENT_NOT_COMPLETED = "Payment not completed";
    static final String PAYMENT_MISMATCH = "Payment does not match this order";

    private final StripeProps props;
    private final StripeClient stripe;

    @PostConstruct
    void report() {
        if (props.configured()) log.info("Ticket payments: Stripe test mode ({})", props.currencyOrDefault());
        else if (props.liveKeysRefused()) log.warn("Ticket payments: Stripe keys are not test keys (sk_test_/pk_test_), using the demo payment");
        else log.info("Ticket payments: demo (no Stripe keys)");
    }

    public boolean enabled() {
        return props.configured();
    }

    public String publishableKey() {
        return props.publishableKey().trim();
    }

    public String currency() {
        return props.currencyOrDefault();
    }

    /** Totalul în subunități (bani). */
    public static long minor(BigDecimal total) {
        return total.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    public StripeClient.Intent prepare(Event e, int qtyAdult, int qtyChild, BigDecimal total, CurrentUser user) {
        Map<String, String> meta = Map.of("eventId", e.getId().toString(), "userId", user.id().toString(),
                "qtyAdult", Integer.toString(qtyAdult), "qtyChild", Integer.toString(qtyChild), "app", "aradexperience");
        String description = "AradExperience · " + truncate(e.getTitle(), 120) + " · " + qtyAdult + "+" + qtyChild;
        StripeClient.Intent pi = stripe.create(minor(total), description, meta, UUID.randomUUID().toString());
        log.info("Stripe PaymentIntent {} for event {} ({} bani)", pi.id(), e.getId(), pi.amount());
        return pi;
    }

    /** Plata trebuie să fie reușită și să fie exact pentru comanda asta (eveniment, utilizator, cantități, sumă, monedă). */
    public void verify(String paymentIntentId, Event e, int qtyAdult, int qtyChild, BigDecimal total, CurrentUser user) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) throw new BadRequestException(PAYMENT_REQUIRED);
        StripeClient.Intent pi = stripe.retrieve(paymentIntentId.trim());
        if (!"succeeded".equals(pi.status())) throw new ApiException(HttpStatus.PAYMENT_REQUIRED, PAYMENT_NOT_COMPLETED);
        Map<String, String> m = pi.metadata();
        boolean matches = e.getId().toString().equals(m.get("eventId")) && user.id().toString().equals(m.get("userId"))
                && Integer.toString(qtyAdult).equals(m.get("qtyAdult")) && Integer.toString(qtyChild).equals(m.get("qtyChild"))
                && pi.amount() == minor(total) && currency().equalsIgnoreCase(pi.currency());
        if (!matches) {
            log.warn("PaymentIntent {} does not match the order for event {}", pi.id(), e.getId());
            throw new ConflictException(PAYMENT_MISMATCH);
        }
    }

    /**
     * Biletul nu s-a mai putut emite după o plată reușită (ex. locurile s-au terminat între timp): banii (de test) se întorc.
     * Doar pentru o plată reușită a acestui utilizator (nu rambursăm plata altcuiva trimisă cu id-ul ei). Nu aruncă.
     */
    public void refund(String paymentIntentId, CurrentUser user) {
        if (!enabled() || paymentIntentId == null || paymentIntentId.isBlank()) return;
        try {
            StripeClient.Intent pi = stripe.retrieve(paymentIntentId.trim());
            if ("succeeded".equals(pi.status()) && user.id().toString().equals(pi.metadata().get("userId"))) stripe.refundQuietly(pi.id());
        } catch (ApiException e) {
            log.warn("Could not check PaymentIntent {} for a refund", paymentIntentId);
        }
    }

    private static String truncate(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max);
    }
}
