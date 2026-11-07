package aradexperience.dto.payments;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** docs/API.md §Plăți (T25): `POST /payments/{paymentIntentId}/confirm` → `{ email? }` (e-mailul pentru bilete, opțional). */
public record PaymentConfirm(@Email @Size(max = 190) String email) {
}
