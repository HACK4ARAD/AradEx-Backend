package aradexperience.controller.payments;

import aradexperience.controller.ControllerTest;
import aradexperience.exceptions.BadRequestException;
import aradexperience.service.payments.StripeWebhook;
import aradexperience.service.payments.TicketPayments;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(PaymentsController.class)
class PaymentsControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TicketPayments payments;
    @MockBean StripeWebhook webhook;

    @Test
    void config_isPublic() throws Exception {
        when(payments.enabled()).thenReturn(true);
        when(payments.currency()).thenReturn("ron");
        mvc.perform(get("/api/v1/payments/config"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.cardPayments").value(true))
           .andExpect(jsonPath("$.currency").value("ron"))
           .andExpect(jsonPath("$.testMode").value(true));
    }

    @Test
    void webhook_isPublic_getsTheRawBody_andABadSignatureIs400() throws Exception {
        String body = "{\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{}}}";
        mvc.perform(post("/api/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).header("Stripe-Signature", "t=1,v1=x").content(body))
           .andExpect(status().isOk());
        verify(webhook).handle(body, "t=1,v1=x");

        doThrow(new BadRequestException("Invalid Stripe signature")).when(webhook).handle(any(), any());
        mvc.perform(post("/api/v1/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }
}
