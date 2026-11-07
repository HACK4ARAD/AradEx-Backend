package aradexperience.service.payments;

import aradexperience.entity.events.Event;
import aradexperience.enums.Role;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.mock.EventMock;
import aradexperience.mock.TicketMock;
import aradexperience.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plata se acceptă doar dacă Stripe confirmă o plată reușită pentru exact comanda asta. */
class TicketPaymentsTest {

    StripeClient stripe = mock(StripeClient.class);
    TicketPayments payments = new TicketPayments(new StripeProps("sk_test_1", "pk_test_2", "ron", null), stripe);
    Event puppets = TicketMock.puppets(0);
    CurrentUser ana = EventMock.user(7, Role.LOCAL);
    BigDecimal total = new BigDecimal("105");

    static StripeClient.Intent intent(String status, long amount, String userId) {
        return new StripeClient.Intent("pi_1", "secret", status, amount, "ron",
                Map.of("eventId", "22", "userId", userId, "qtyAdult", "2", "qtyChild", "1"));
    }

    @Test
    void minor_isInBani() {
        assertThat(TicketPayments.minor(new BigDecimal("13.5"))).isEqualTo(1350);
        assertThat(TicketPayments.minor(new BigDecimal("105"))).isEqualTo(10500);
    }

    @Test
    void prepare_createsIntentForTheServerTotal_withOrderMetadata() {
        when(stripe.create(anyLong(), anyString(), anyMap(), anyString())).thenReturn(intent("requires_payment_method", 10500, "7"));
        payments.prepare(puppets, 2, 1, total, ana);
        verify(stripe).create(eq(10500L), any(), eq(Map.of("eventId", "22", "userId", "7", "qtyAdult", "2", "qtyChild", "1", "app", "aradexperience")), any());
    }

    @Test
    void verify_acceptsASucceededMatchingPayment() {
        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 10500, "7"));
        assertThatCode(() -> payments.verify("pi_1", puppets, 2, 1, total, ana)).doesNotThrowAnyException();
    }

    @Test
    void verify_rejectsMissing_unpaid_andMismatchedPayments() {
        assertThatThrownBy(() -> payments.verify(" ", puppets, 2, 1, total, ana)).isInstanceOf(BadRequestException.class);

        when(stripe.retrieve("pi_1")).thenReturn(intent("requires_payment_method", 10500, "7"));
        assertThatThrownBy(() -> payments.verify("pi_1", puppets, 2, 1, total, ana))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.PAYMENT_REQUIRED));

        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 4000, "7")); // altă sumă
        assertThatThrownBy(() -> payments.verify("pi_1", puppets, 2, 1, total, ana)).isInstanceOf(ConflictException.class);

        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 10500, "99")); // plata altcuiva
        assertThatThrownBy(() -> payments.verify("pi_1", puppets, 2, 1, total, ana)).isInstanceOf(ConflictException.class);

        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 10500, "7"));
        assertThatThrownBy(() -> payments.verify("pi_1", puppets, 3, 0, total, ana)).isInstanceOf(ConflictException.class); // alte cantități
    }

    @Test
    void refund_onlyForTheBuyersOwnSucceededPayment() {
        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 10500, "99"));
        payments.refund("pi_1", ana);
        verify(stripe, never()).refundQuietly(any());

        when(stripe.retrieve("pi_1")).thenReturn(intent("succeeded", 10500, "7"));
        payments.refund("pi_1", ana);
        verify(stripe).refundQuietly("pi_1");
    }

    @Test
    void withoutTestKeys_stripeIsOff() {
        TicketPayments off = new TicketPayments(new StripeProps("sk_live_x", "pk_live_y", null, null), stripe);
        assertThat(off.enabled()).isFalse();
        off.refund("pi_1", ana);
        verify(stripe, never()).retrieve(any());
    }
}
