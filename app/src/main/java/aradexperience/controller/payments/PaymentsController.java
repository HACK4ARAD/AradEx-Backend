package aradexperience.controller.payments;

import aradexperience.api.PaymentsApi;
import aradexperience.dto.payments.CheckoutDto;
import aradexperience.dto.payments.PaymentConfigDto;
import aradexperience.dto.payments.PaymentConfirm;
import aradexperience.dto.tickets.TicketDto;
import aradexperience.dto.tickets.TicketRequest;
import aradexperience.security.AuthContext;
import aradexperience.service.payments.PaymentService;
import aradexperience.service.tickets.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static aradexperience.mapper.tickets.TicketMapper.toDto;

@RestController
@RequiredArgsConstructor
public class PaymentsController implements PaymentsApi {

    private final PaymentService service;

    @Override
    public PaymentConfigDto config() {
        PaymentService.Config c = service.config();
        return new PaymentConfigDto(c.enabled(), c.publishableKey(), c.currency(), true);
    }

    @Override
    public CheckoutDto checkout(long id, TicketRequest request) {
        PaymentService.Checkout c = service.checkout(id, request, AuthContext.require());
        return new CheckoutDto(c.paymentIntentId(), c.clientSecret(), c.amount().doubleValue(), c.currency());
    }

    @Override
    public TicketDto confirm(String paymentIntentId, PaymentConfirm request) {
        TicketService.TicketView v = service.confirm(paymentIntentId, request == null ? null : request.email(), AuthContext.require());
        return toDto(v.ticket(), v.event());
    }

    @Override
    public void webhook(String payload, String signature) {
        service.webhook(payload, signature);
    }
}
