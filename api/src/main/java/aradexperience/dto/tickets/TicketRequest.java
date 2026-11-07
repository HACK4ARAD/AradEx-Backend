package aradexperience.dto.tickets;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * POST /events/{id}/tickets. Maximum 10 bilete pe comandă (ca în S4); la „Înscrie-te · gratuit” FE trimite qtyAdult = 1.
 * `email` e opțional (T21: conturile personale au telefon, nu email); dacă e trimis, trebuie să fie valid.
 */
public record TicketRequest(
        @Min(0) @Max(10) int qtyAdult,
        @Min(0) @Max(10) int qtyChild,
        @Email @Size(max = 190) String email) {
}
