package aradexperience.service.payments;

import aradexperience.exceptions.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T25 · apelurile spre API-ul Stripe (https://api.stripe.com/v1, formular + cheia secretă de test), fără SDK: avem nevoie doar de
 * trei operații — PaymentIntent nou, citirea lui, rambursarea. Cheia secretă stă doar aici (backend), niciodată în aplicație.
 */
@Slf4j
@Component
public class StripeClient {

    static final String UNAVAILABLE = "Card payments are unavailable";

    /** Ce ne trebuie dintr-un PaymentIntent: suma e în subunități (bani), `metadata` = comanda (eveniment, utilizator, bilete). */
    public record PaymentIntent(String id, String clientSecret, String status, long amount, String currency, Map<String, String> metadata) {

        static PaymentIntent of(JsonNode n) {
            Map<String, String> meta = new LinkedHashMap<>();
            n.path("metadata").fields().forEachRemaining(f -> meta.put(f.getKey(), f.getValue().asText()));
            return new PaymentIntent(n.path("id").asText(null), n.path("client_secret").asText(null), n.path("status").asText(null),
                    n.path("amount").asLong(), n.path("currency").asText(null), meta);
        }
    }

    private final StripeProps props;
    private final RestClient http;

    @Autowired
    public StripeClient(StripeProps props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrlOrDefault()).defaultHeaders(h -> {
            if (props.enabled()) h.setBearerAuth(props.secretKey().trim());
            h.set("Stripe-Version", "2024-06-20");
        }).build();
    }

    /** PaymentIntent doar pentru card (fără metode cu redirect), cu comanda în `metadata`. */
    public PaymentIntent createPaymentIntent(long amount, String currency, String description, Map<String, String> metadata,
                                             String idempotencyKey) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("amount", Long.toString(amount));
        f.add("currency", currency);
        f.add("payment_method_types[]", "card");
        f.add("description", description);
        metadata.forEach((k, v) -> { if (v != null) f.add("metadata[" + k + "]", v); });
        return PaymentIntent.of(call(() -> http.post().uri("/v1/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Idempotency-Key", idempotencyKey)
                .body(f).retrieve().body(JsonNode.class)));
    }

    public PaymentIntent retrieve(String paymentIntentId) {
        return PaymentIntent.of(call(() -> http.get().uri("/v1/payment_intents/{id}", paymentIntentId).retrieve().body(JsonNode.class)));
    }

    /** Rambursare integrală (ex. locurile s-au epuizat între plată și emiterea biletului). */
    public void refund(String paymentIntentId) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("payment_intent", paymentIntentId);
        call(() -> http.post().uri("/v1/refunds").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Idempotency-Key", "refund-" + paymentIntentId).body(f).retrieve().body(JsonNode.class));
        log.info("Refunded payment {}", paymentIntentId);
    }

    private JsonNode call(java.util.function.Supplier<JsonNode> request) {
        if (!props.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        try {
            JsonNode body = request.get();
            if (body == null) throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
            return body;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) throw new ApiException(HttpStatus.NOT_FOUND, "Payment not found");
            if (status == 401 || status == 403) log.error("Stripe rejected the API key: HTTP {}", status);
            else log.warn("Stripe request failed: HTTP {}", status);
            throw new ApiException(status >= 500 ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Stripe unreachable: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }
}
