package aradexperience.controller.payments;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.events.Event;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.TicketMock;
import aradexperience.service.payments.PaymentService;
import aradexperience.service.tickets.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(PaymentsController.class)
class PaymentsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PaymentService service;

    @Test
    void config_isPublic() throws Exception {
        when(service.config()).thenReturn(new PaymentService.Config(true, "pk_test_1", "ron"));
        mvc.perform(get("/api/v1/payments/config"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.enabled").value(true))
           .andExpect(jsonPath("$.publishableKey").value("pk_test_1"))
           .andExpect(jsonPath("$.testMode").value(true));
    }

    @Test
    void checkout_needsAnAccount_andReturnsTheClientSecret() throws Exception {
        String body = "{\"qtyAdult\":2,\"qtyChild\":1,\"email\":\"ana@exemplu.ro\"}";
        mvc.perform(post("/api/v1/events/22/tickets/checkout").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isUnauthorized());
        when(service.checkout(eq(22L), any(), any())).thenReturn(new PaymentService.Checkout("pi_1", "pi_1_secret", new BigDecimal("105"), "ron"));
        mvc.perform(post("/api/v1/events/22/tickets/checkout").with(AuthMock.as(7, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.clientSecret").value("pi_1_secret"))
           .andExpect(jsonPath("$.amount").value(105.0));
    }

    @Test
    void confirm_returnsTheTicket_orA409WhileUnpaid() throws Exception {
        Event e = TicketMock.puppets(89);
        when(service.confirm(eq("pi_1"), isNull(), any())).thenReturn(new TicketService.TicketView(TicketMock.ticket("AX-T22-ABCDEF", e, 7L), e));
        mvc.perform(post("/api/v1/payments/pi_1/confirm").with(AuthMock.as(7, Role.LOCAL)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.code").value("AX-T22-ABCDEF"));
        when(service.confirm(eq("pi_2"), any(), any())).thenThrow(new ConflictException("Payment not completed"));
        mvc.perform(post("/api/v1/payments/pi_2/confirm").with(AuthMock.as(7, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.ro\"}"))
           .andExpect(status().isConflict());
    }

    @Test
    void webhook_isPublic_andGetsTheRawBodyAndSignature() throws Exception {
        String body = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{}}}";
        mvc.perform(post("/api/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=x").content(body))
           .andExpect(status().isOk());
        verify(service).webhook(body, "t=1,v1=x");
    }
}
