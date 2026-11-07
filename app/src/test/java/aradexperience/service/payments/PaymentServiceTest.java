package aradexperience.service.payments;

import aradexperience.dto.tickets.TicketRequest;
import aradexperience.entity.events.Event;
import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.Role;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.EventMock;
import aradexperience.mock.TicketMock;
import aradexperience.service.tickets.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T25 · Verificare: comanda → PaymentIntent (suma din prețuri), plata reușită → bilet o singură dată, locuri epuizate → rambursare. */
class PaymentServiceTest {

    static final StripeProps ON = new StripeProps("sk_test_1", "pk_test_1", "whsec_1", null, "ron");
    static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    static final Map<String, String> META = Map.of("source", "aradexperience", "eventId", "22", "userId", "7", "qtyAdult", "2",
            "qtyChild", "1", "email", "ana@exemplu.ro");

    StripeClient stripe = mock(StripeClient.class);
    TicketService tickets = mock(TicketService.class);
    PaymentService service = service(ON);

    PaymentService service(StripeProps props) {
        PaymentService s = new PaymentService(props, stripe, tickets, new ObjectMapper());
        s.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return s;
    }

    static StripeClient.PaymentIntent pi(String status, Map<String, String> meta) {
        return new StripeClient.PaymentIntent("pi_1", "pi_1_secret", status, 10500, "ron", meta);
    }

    TicketService.TicketView view() {
        Event e = TicketMock.puppets(89);
        Ticket t = TicketMock.ticket("AX-T22-ABCDEF", e, 7L);
        return new TicketService.TicketView(t, e);
    }

    @BeforeEach
    void setUp() {
        when(tickets.issuePaid(anyLong(), any(), anyLong(), anyString(), any())).thenAnswer(inv -> view());
    }

    @Test
    void config_showsThePublishableKeyOnlyWhenOn() {
        assertThat(service.config()).isEqualTo(new PaymentService.Config(true, "pk_test_1", "ron"));
        assertThat(new StripeCardRequirement(ON).cardPaymentRequired()).isTrue();
        PaymentService off = service(new StripeProps("", "", "", null, null));
        assertThat(off.config().enabled()).isFalse();
        assertThat(off.config().publishableKey()).isNull();
        assertThat(new StripeCardRequirement(new StripeProps("", "", "", null, null)).cardPaymentRequired()).isFalse();
        assertThatThrownBy(() -> off.checkout(22L, new TicketRequest(1, 0, null), EventMock.user(7, Role.LOCAL)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(503));
    }

    @SuppressWarnings("unchecked")
    @Test
    void checkout_amountInBaniFromTheEventPrices_orderInMetadata() {
        when(tickets.quote(eq(22L), any(), any())).thenReturn(new TicketService.Quote(TicketMock.puppets(86), new BigDecimal("105")));
        when(stripe.createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString())).thenReturn(pi("requires_payment_method", META));

        PaymentService.Checkout c = service.checkout(22L, new TicketRequest(2, 1, " ana@exemplu.ro "), EventMock.user(7, Role.LOCAL));

        ArgumentCaptor<Map<String, String>> meta = ArgumentCaptor.forClass(Map.class);
        verify(stripe).createPaymentIntent(eq(10500L), eq("ron"), anyString(), meta.capture(), anyString());
        assertThat(meta.getValue()).containsEntry("source", "aradexperience").containsEntry("eventId", "22").containsEntry("userId", "7")
                .containsEntry("qtyAdult", "2").containsEntry("qtyChild", "1").containsEntry("email", "ana@exemplu.ro");
        assertThat(c.clientSecret()).isEqualTo("pi_1_secret");
        assertThat(c.amount()).isEqualByComparingTo("105");
    }

    @Test
    void checkout_freeOrder_is400() {
        when(tickets.quote(eq(30L), any(), any())).thenReturn(new TicketService.Quote(TicketMock.freeWalk(20), BigDecimal.ZERO));
        assertThatThrownBy(() -> service.checkout(30L, new TicketRequest(1, 0, null), EventMock.user(7, Role.LOCAL)))
                .isInstanceOf(BadRequestException.class);
        verify(stripe, never()).createPaymentIntent(anyLong(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void confirm_succeeded_issuesTheTicketWithThePaidAmount() {
        when(stripe.retrieve("pi_1")).thenReturn(pi("succeeded", META));

        service.confirm("pi_1", null, EventMock.user(7, Role.LOCAL));

        ArgumentCaptor<TicketRequest> req = ArgumentCaptor.forClass(TicketRequest.class);
        verify(tickets).issuePaid(eq(22L), req.capture(), eq(7L), eq("pi_1"), eq(new BigDecimal("105.00")));
        assertThat(req.getValue()).isEqualTo(new TicketRequest(2, 1, "ana@exemplu.ro"));
    }

    @Test
    void confirm_notPaidYet_is409_anotherUser_is403_foreignIntent_is404() {
        when(stripe.retrieve("pi_1")).thenReturn(pi("requires_payment_method", META));
        assertThatThrownBy(() -> service.confirm("pi_1", null, EventMock.user(7, Role.LOCAL))).isInstanceOf(ConflictException.class);
        when(stripe.retrieve("pi_1")).thenReturn(pi("succeeded", META));
        assertThatThrownBy(() -> service.confirm("pi_1", null, EventMock.user(8, Role.LOCAL))).isInstanceOf(ForbiddenException.class);
        when(stripe.retrieve("pi_1")).thenReturn(pi("succeeded", Map.of("eventId", "22")));
        assertThatThrownBy(() -> service.confirm("pi_1", null, EventMock.user(7, Role.LOCAL))).isInstanceOf(NotFoundException.class);
        verify(tickets, never()).issuePaid(anyLong(), any(), anyLong(), anyString(), any());
    }

    @Test
    void confirm_soldOutMeanwhile_refundsAndSaysSo() {
        when(stripe.retrieve("pi_1")).thenReturn(pi("succeeded", META));
        when(tickets.issuePaid(anyLong(), any(), anyLong(), anyString(), any())).thenThrow(new ConflictException("Sold out"));
        assertThatThrownBy(() -> service.confirm("pi_1", null, EventMock.user(7, Role.LOCAL)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("refunded");
        verify(stripe).refund("pi_1");
    }

    @Test
    void webhook_signedSucceededEvent_issuesTheTicket_otherEventsAndBadSignaturesDoNot() {
        String body = """
                {"type":"payment_intent.succeeded","data":{"object":{"id":"pi_1","status":"succeeded","amount":10500,"currency":"ron",
                 "metadata":{"source":"aradexperience","eventId":"22","userId":"7","qtyAdult":"2","qtyChild":"1"}}}}""";
        service.webhook(body, StripeSignature.sign(body, "whsec_1", NOW.getEpochSecond()));
        verify(tickets).issuePaid(eq(22L), any(), eq(7L), eq("pi_1"), eq(new BigDecimal("105.00")));

        assertThatThrownBy(() -> service.webhook(body, StripeSignature.sign(body, "whsec_other", NOW.getEpochSecond())))
                .isInstanceOf(BadRequestException.class);
        String other = "{\"type\":\"charge.refunded\",\"data\":{\"object\":{}}}";
        service.webhook(other, StripeSignature.sign(other, "whsec_1", NOW.getEpochSecond()));
        verify(tickets).issuePaid(anyLong(), any(), anyLong(), anyString(), any()); // tot o singură dată
    }

    @Test
    void webhook_withoutSecret_is503() {
        PaymentService noHook = service(new StripeProps("sk_test_1", "pk_test_1", "", null, null));
        assertThatThrownBy(() -> noHook.webhook("{}", "t=1,v1=x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(503));
    }
}
