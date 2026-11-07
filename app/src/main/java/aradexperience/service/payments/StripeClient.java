package aradexperience.service.payments;

import aradexperience.exceptions.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stripe PaymentIntents prin RestClient (fără SDK, ca Twilio): creare, citire, rambursare. Doar plăți cu card, fără redirect
 * (`payment_method_types[]=card`), ca plata să se termine în aceeași pagină, pe web și în aplicație. Erorile Stripe devin
 * 502 „Payment provider unavailable” (sau 400 pentru o cerere refuzată), niciodată 500; cheia nu apare în loguri.
 */
@Slf4j
@Component
public class StripeClient {

    static final String UNAVAILABLE = "Payment provider unavailable";

    private final StripeProps props;
    private final RestClient http;

    @Autowired
    public StripeClient(StripeProps props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrlOrDefault()).defaultHeaders(h -> {
            if (props.configured()) h.setBearerAuth(props.secretKey().trim());
            h.set("Stripe-Version", "2024-06-20");
        }).build();
    }

    /** Ce ne trebuie dintr-un PaymentIntent. `amount` e în subunități (bani: 1 leu = 100). */
    public record Intent(String id, String clientSecret, String status, long amount, String currency, Map<String, String> metadata) {}

    public Intent create(long amount, String description, Map<String, String> metadata, String idempotencyKey) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", Long.toString(amount));
        form.add("currency", props.currencyOrDefault());
        form.add("payment_method_types[]", "card");
        form.add("description", description);
        metadata.forEach((k, v) -> form.add("metadata[" + k + "]", v));
        return call(() -> http.post().uri("/v1/payment_intents").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.set("Idempotency-Key", idempotencyKey)).body(form).retrieve().body(Map.class));
    }

    public Intent retrieve(String id) {
        return call(() -> http.get().uri("/v1/payment_intents/{id}", id).retrieve().body(Map.class));
    }

    /** Rambursează tot (ex. biletul n-a mai putut fi emis după plată). Nu aruncă: o rambursare ratată se vede în log și în Stripe. */
    public void refundQuietly(String paymentIntentId) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("payment_intent", paymentIntentId);
            http.post().uri("/v1/refunds").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
            log.info("Stripe refund for {}", paymentIntentId);
        } catch (RestClientException e) {
            log.warn("Stripe refund failed for {}: {}", paymentIntentId, e.getClass().getSimpleName());
        }
    }

    private Intent call(java.util.function.Supplier<Map<?, ?>> request) {
        try {
            return toIntent(request.get());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("Stripe refused the request: {}", e.getStatusCode());
            if (e.getStatusCode().value() == 404) throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown payment");
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("Stripe unavailable: {}", e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }
    }

    static Intent toIntent(Map<?, ?> body) {
        if (body == null || body.get("id") == null) throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        Map<String, String> meta = new LinkedHashMap<>();
        if (body.get("metadata") instanceof Map<?, ?> m) m.forEach((k, v) -> meta.put(String.valueOf(k), String.valueOf(v)));
        Object amount = body.get("amount");
        return new Intent(String.valueOf(body.get("id")), body.get("client_secret") == null ? null : String.valueOf(body.get("client_secret")),
                String.valueOf(body.get("status")), amount instanceof Number n ? n.longValue() : -1,
                String.valueOf(body.get("currency")), meta);
    }
}
