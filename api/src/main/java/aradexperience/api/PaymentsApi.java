package aradexperience.api;

import aradexperience.dto.payments.PaymentConfigDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * docs/API.md §Plăți (T25): plata biletelor cu Stripe în mod test — configurația publică și webhook-ul Stripe. Plata însăși e în
 * TicketsApi (`POST /events/{id}/tickets/payment`, apoi `POST /events/{id}/tickets` cu `paymentIntentId`).
 * Implementat de `controller/payments/PaymentsController`.
 */
@Tag(name = "Payments")
@RequestMapping("/api/v1/payments")
public interface PaymentsApi {

    @Operation(summary = "Dacă biletele se plătesc cu cardul (Stripe, mod test) sau cu plata demonstrativă (public)")
    @GetMapping("/config")
    PaymentConfigDto config();

    @Operation(summary = "Webhook Stripe: payment_intent.succeeded emite biletul dacă aplicația nu l-a cerut deja (public, semnat)")
    @PostMapping("/stripe/webhook")
    void webhook(@RequestBody String payload, @RequestHeader(name = "Stripe-Signature", required = false) String signature);
}
