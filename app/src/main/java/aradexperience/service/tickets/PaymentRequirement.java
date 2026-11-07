package aradexperience.service.tickets;

/**
 * T25: dacă biletele plătite cer plata cu cardul (Stripe în mod test). Implementat de `service/payments/PaymentService`;
 * fără el (sau fără cheile Stripe) biletele se emit direct, cu plata demonstrativă din T07.
 */
public interface PaymentRequirement {

    boolean cardPaymentRequired();
}
