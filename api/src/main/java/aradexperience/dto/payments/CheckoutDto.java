package aradexperience.dto.payments;

/**
 * docs/API.md §Plăți (T25): răspunsul la `POST /events/{id}/tickets/checkout` — PaymentIntent-ul Stripe (mod test) pentru comandă.
 * `clientSecret` merge în Payment Element (Stripe.js); `amount` în lei.
 */
public record CheckoutDto(String paymentIntentId, String clientSecret, double amount, String currency) {
}
