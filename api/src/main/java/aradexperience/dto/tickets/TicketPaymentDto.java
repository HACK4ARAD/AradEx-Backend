package aradexperience.dto.tickets;

import aradexperience.enums.PaymentMode;

import java.math.BigDecimal;

/**
 * POST /events/{id}/tickets/payment → cum se plătește comanda. STRIPE: `clientSecret` + `publishableKey` pentru Stripe.js, apoi
 * POST /events/{id}/tickets cu `paymentIntentId`. DEMO: fără Stripe (chei lipsă, eveniment gratuit), direct POST /events/{id}/tickets.
 * `amount` = totalul calculat de server, în lei; `currency` = moneda Stripe (`ron`).
 */
public record TicketPaymentDto(PaymentMode mode, BigDecimal amount, String currency, String paymentIntentId, String clientSecret,
                               String publishableKey) {
}
