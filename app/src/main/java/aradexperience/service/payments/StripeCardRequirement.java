package aradexperience.service.payments;

import aradexperience.service.tickets.PaymentRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * T25: cu cheile Stripe de test puse, biletele plătite se iau doar prin plata cu cardul. Bean separat de PaymentService
 * (care folosește TicketService), ca TicketService să nu depindă circular de el.
 */
@Component
@RequiredArgsConstructor
public class StripeCardRequirement implements PaymentRequirement {

    private final StripeProps props;

    @Override
    public boolean cardPaymentRequired() {
        return props.enabled();
    }
}
