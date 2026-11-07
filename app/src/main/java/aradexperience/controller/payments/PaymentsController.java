package aradexperience.controller.payments;

import aradexperience.api.PaymentsApi;
import aradexperience.dto.payments.PaymentConfigDto;
import aradexperience.service.payments.StripeWebhook;
import aradexperience.service.payments.TicketPayments;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentsController implements PaymentsApi {

    private final TicketPayments payments;
    private final StripeWebhook webhook;

    @Override
    public PaymentConfigDto config() {
        return new PaymentConfigDto(payments.enabled(), payments.currency(), true);
    }

    @Override
    public void webhook(String payload, String signature) {
        webhook.handle(payload, signature);
    }
}
