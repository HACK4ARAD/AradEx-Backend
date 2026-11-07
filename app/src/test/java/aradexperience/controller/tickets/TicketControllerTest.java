package aradexperience.controller.tickets;

import aradexperience.controller.ControllerTest;
import aradexperience.entity.events.Event;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.AuthMock;
import aradexperience.mock.JsonFixtures;
import aradexperience.mock.TicketMock;
import aradexperience.service.tickets.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ControllerTest(TicketController.class)
class TicketControllerTest {

    @Autowired MockMvc mvc;
    @MockBean TicketService service;

    private static TicketService.TicketView view() {
        Event e = TicketMock.puppets(88);
        return new TicketService.TicketView(TicketMock.ticket("AX-T22-K8D2QF", e, 7), e);
    }

    @Test
    void buy_returns201_withCodeAndEvent() throws Exception {
        when(service.buy(eq(22L), any(), any())).thenReturn(view());
        mvc.perform(post("/api/v1/events/22/tickets").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/buy.json")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.code").value("AX-T22-K8D2QF"))
           .andExpect(jsonPath("$.eventTitle").value("Teatru de păpuși: Croitorașul cel viteaz"))
           .andExpect(jsonPath("$.total").value(65.0))
           .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void buy_withoutToken_is401_soldOut_is409_badBody_is400() throws Exception {
        mvc.perform(post("/api/v1/events/22/tickets").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/buy.json")))
           .andExpect(status().isUnauthorized());
        when(service.buy(eq(22L), any(), any())).thenThrow(new ConflictException("Sold out"));
        mvc.perform(post("/api/v1/events/22/tickets").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/buy.json")))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Sold out"));
        mvc.perform(post("/api/v1/events/22/tickets").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content("{\"qtyAdult\":11,\"qtyChild\":0,\"email\":\"x\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void mine_requiresLogin() throws Exception {
        mvc.perform(get("/api/v1/me/tickets")).andExpect(status().isUnauthorized());
        when(service.mine(any(), eq(true))).thenReturn(List.of(view()));
        mvc.perform(get("/api/v1/me/tickets").param("upcoming", "true").with(AuthMock.as(7L, Role.LOCAL)))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("AX-T22-K8D2QF"));
    }

    @Test
    void ics_isPublic_textCalendar() throws Exception {
        when(service.ics(22L)).thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");
        mvc.perform(get("/api/v1/events/22/ics"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith("text/calendar"))
           .andExpect(header().string("Content-Disposition", "attachment; filename=\"aradexperience-22.ics\""));
    }

    // --- plata cu Stripe (modul de test) ------------------------------------------------------------------------------

    @Test
    void preparePayment_stripe_returnsClientSecretAndTestKey() throws Exception {
        var e = TicketMock.puppets(0);
        var intent = new aradexperience.service.payments.StripeClient.Intent("pi_1", "pi_1_secret_x", "requires_payment_method", 10500, "ron", java.util.Map.of());
        when(service.preparePayment(eq(22L), any(), any())).thenReturn(new TicketService.Payment(
                new TicketService.Quote(e, 3, new java.math.BigDecimal("105")), intent, "pk_test_1", "ron"));
        mvc.perform(post("/api/v1/events/22/tickets/payment").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/payment.json")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.mode").value("STRIPE"))
           .andExpect(jsonPath("$.clientSecret").value("pi_1_secret_x"))
           .andExpect(jsonPath("$.publishableKey").value("pk_test_1"))
           .andExpect(jsonPath("$.amount").value(105));
    }

    @Test
    void preparePayment_demo_withoutStripe_andNeedsLogin() throws Exception {
        when(service.preparePayment(eq(22L), any(), any())).thenReturn(new TicketService.Payment(
                new TicketService.Quote(TicketMock.puppets(0), 1, new java.math.BigDecimal("40")), null, null, null));
        mvc.perform(post("/api/v1/events/22/tickets/payment").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/payment.json")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("DEMO")).andExpect(jsonPath("$.clientSecret").doesNotExist());
        mvc.perform(post("/api/v1/events/22/tickets/payment").contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/payment.json")))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void buy_withUnpaidIntent_is402() throws Exception {
        when(service.buy(eq(22L), any(), any())).thenThrow(new aradexperience.exceptions.ApiException(org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "Payment not completed"));
        mvc.perform(post("/api/v1/events/22/tickets").with(AuthMock.as(7L, Role.LOCAL)).contentType(MediaType.APPLICATION_JSON).content(JsonFixtures.read("requests/tickets/buy-paid.json")))
           .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.detail").value("Payment not completed"));
    }
}
