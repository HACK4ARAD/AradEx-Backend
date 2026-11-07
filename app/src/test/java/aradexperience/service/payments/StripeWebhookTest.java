package aradexperience.service.payments;

import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.service.tickets.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T25 · Verificare: plata reușită anunțată de Stripe (aplicația închisă) → biletul; semnătura e obligatorie. */
class StripeWebhookTest {

    static final Instant NOW = Instant.parse("2026-11-07T20:00:00Z");
    static final String BODY = """
            {"id":"evt_1","type":"payment_intent.succeeded","data":{"object":{"id":"pi_1","status":"succeeded","amount":10500,"currency":"ron",
             "metadata":{"app":"aradexperience","eventId":"22","userId":"7","qtyAdult":"2","qtyChild":"1"}}}}""";

    TicketPayments payments = mock(TicketPayments.class);
    TicketService tickets = mock(TicketService.class);
    StripeWebhook webhook = webhook("whsec_1");

    StripeWebhook webhook(String secret) {
        StripeWebhook w = new StripeWebhook(new StripeWebhookProps(secret), payments, tickets, new ObjectMapper());
        w.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return w;
    }

    @BeforeEach
    void setUp() {
        when(payments.enabled()).thenReturn(true);
        when(tickets.issueFromPayment(any())).thenReturn(Optional.empty());
    }

    @Test
    void signedSucceededEvent_passesThePaymentIntentToTheTickets() {
        webhook.handle(BODY, StripeSignature.sign(BODY, "whsec_1", NOW.getEpochSecond()));

        ArgumentCaptor<StripeClient.Intent> pi = ArgumentCaptor.forClass(StripeClient.Intent.class);
        verify(tickets).issueFromPayment(pi.capture());
        assertThat(pi.getValue().id()).isEqualTo("pi_1");
        assertThat(pi.getValue().status()).isEqualTo("succeeded");
        assertThat(pi.getValue().amount()).isEqualTo(10500);
        assertThat(pi.getValue().metadata()).containsEntry("eventId", "22").containsEntry("userId", "7").containsEntry("app", "aradexperience");
    }

    @Test
    void wrongSignature_is400_otherEvents_areIgnored() {
        assertThatThrownBy(() -> webhook.handle(BODY, StripeSignature.sign(BODY, "whsec_other", NOW.getEpochSecond())))
                .isInstanceOf(BadRequestException.class);
        String other = "{\"type\":\"charge.refunded\",\"data\":{\"object\":{}}}";
        webhook.handle(other, StripeSignature.sign(other, "whsec_1", NOW.getEpochSecond()));
        verify(tickets, never()).issueFromPayment(any());
    }

    @Test
    void withoutSecretOrWithoutStripe_is503() {
        assertThatThrownBy(() -> webhook("").handle(BODY, "t=1,v1=x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(503));
        when(payments.enabled()).thenReturn(false);
        assertThatThrownBy(() -> webhook.handle(BODY, StripeSignature.sign(BODY, "whsec_1", NOW.getEpochSecond())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(503));
    }
}
