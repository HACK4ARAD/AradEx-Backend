package aradexperience.dto.tickets;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * POST /events/{id}/tickets (și POST /events/{id}/tickets/payment). Maximum 10 bilete pe comandă (ca în S4); la „Înscrie-te · gratuit”
 * FE trimite qtyAdult = 1. `email` e opțional (T21: conturile personale au telefon, nu email); dacă e trimis, trebuie să fie valid.
 * `paymentIntentId`: PaymentIntent-ul Stripe plătit (modul de test), obligatoriu la biletele IN_APP cu preț când plățile Stripe sunt
 * pornite; fără chei Stripe se ignoră (plată demonstrativă).
 */
public record TicketRequest(
        @Min(0) @Max(10) int qtyAdult,
        @Min(0) @Max(10) int qtyChild,
        @Email @Size(max = 190) String email,
        @Size(max = 100) String paymentIntentId) {

    public TicketRequest(int qtyAdult, int qtyChild, String email) {
        this(qtyAdult, qtyChild, email, null);
    }
}
