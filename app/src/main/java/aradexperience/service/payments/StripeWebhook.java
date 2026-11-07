package aradexperience.service.payments;

import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.service.tickets.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T25 · webhook-ul Stripe (`POST /payments/stripe/webhook`): Stripe anunță singur plata reușită, așa că biletul se emite și când
 * aplicația s-a închis imediat după plată (înainte să ceară biletul). Semnătura (`Stripe-Signature`, HMAC-SHA256 cu `whsec_…`)
 * e obligatorie; se ia în seamă doar `payment_intent.succeeded` pentru PaymentIntent-urile create de noi. Biletul e același ca din
 * aplicație (un PaymentIntent = un bilet), deci ordinea în care ajung webhook-ul și aplicația nu contează.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhook {

    private final StripeWebhookProps props;
    private final TicketPayments payments;
    private final TicketService tickets;
    private final ObjectMapper json;
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public void handle(String payload, String signature) {
        if (!payments.enabled() || !props.configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook not configured");
        if (!StripeSignature.valid(payload, signature, props.secret().trim(), Instant.now(clock))) throw new BadRequestException("Invalid Stripe signature");
        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (java.io.IOException e) {
            throw new BadRequestException("Invalid Stripe event");
        }
        if (!"payment_intent.succeeded".equals(event.path("type").asText())) return;
        JsonNode o = event.path("data").path("object");
        Map<String, String> meta = new LinkedHashMap<>();
        o.path("metadata").fields().forEachRemaining(f -> meta.put(f.getKey(), f.getValue().asText()));
        StripeClient.Intent pi = new StripeClient.Intent(o.path("id").asText(null), null, o.path("status").asText(null),
                o.path("amount").asLong(), o.path("currency").asText(null), meta);
        tickets.issueFromPayment(pi).ifPresent(v -> log.info("Webhook: ticket {} for payment {}", v.ticket().getCode(), pi.id()));
    }
}
