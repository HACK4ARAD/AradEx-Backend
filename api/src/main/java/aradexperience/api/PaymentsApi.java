package aradexperience.api;

import aradexperience.dto.payments.CheckoutDto;
import aradexperience.dto.payments.PaymentConfigDto;
import aradexperience.dto.payments.PaymentConfirm;
import aradexperience.dto.tickets.TicketDto;
import aradexperience.dto.tickets.TicketRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * docs/API.md §Plăți (T25): plata biletelor cu cardul prin Stripe în **mod test** (fără bani reali).
 * Implementat de `controller/payments/PaymentsController`.
 */
@Tag(name = "Payments")
@RequestMapping("/api/v1")
public interface PaymentsApi {

    @Operation(summary = "Dacă plata cu cardul (Stripe, mod test) e pornită + cheia publică pentru Stripe.js (public)")
    @GetMapping("/payments/config")
    PaymentConfigDto config();

    @Operation(summary = "Începe plata unei comenzi de bilete: PaymentIntent Stripe (mod test) cu suma din prețurile evenimentului")
    @PostMapping("/events/{id}/tickets/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    CheckoutDto checkout(@PathVariable("id") long id, @Valid @RequestBody TicketRequest request);

    @Operation(summary = "Biletul pentru o plată reușită (Stripe confirmă plata); 409 dacă plata nu e finalizată; idempotent")
    @PostMapping("/payments/{paymentIntentId}/confirm")
    TicketDto confirm(@PathVariable("paymentIntentId") String paymentIntentId, @Valid @RequestBody(required = false) PaymentConfirm request);

    @Operation(summary = "Webhook Stripe (payment_intent.succeeded), cu semnătura din header-ul Stripe-Signature (public)")
    @PostMapping("/payments/stripe/webhook")
    void webhook(@RequestBody String payload, @RequestHeader(name = "Stripe-Signature", required = false) String signature);
}
