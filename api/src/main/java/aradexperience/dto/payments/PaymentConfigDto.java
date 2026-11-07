package aradexperience.dto.payments;

/**
 * docs/API.md §Plăți (T25): `GET /payments/config` — dacă biletele se plătesc cu cardul prin Stripe în mod test (`STRIPE`) sau cu
 * plata demonstrativă (`DEMO`); aplicația schimbă după asta textele de lângă bilete. Cheia publică vine tot la plată
 * (`POST /events/{id}/tickets/payment`), nu aici.
 */
public record PaymentConfigDto(boolean cardPayments, String currency, boolean testMode) {
}
