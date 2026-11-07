package aradexperience.dto.tickets;

import aradexperience.dto.DtoJson;
import aradexperience.enums.PaymentMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import static org.assertj.core.api.Assertions.assertThat;

/** Plata biletelor cu Stripe (modul de test): TicketPaymentDto și TicketRequest cu paymentIntentId. */
class TicketPaymentDtoTest {

    @Test
    void readsStripeAndDemoModes_andThePaidRequest() {
        TicketPaymentDto s = DtoJson.fromResource("dto/tickets/ticket-payment-stripe.json", TicketPaymentDto.class);
        assertThat(s.mode()).isEqualTo(PaymentMode.STRIPE);
        assertThat(s.publishableKey()).startsWith("pk_test_");
        assertThat(DtoJson.fromResource("dto/tickets/ticket-payment-demo.json", TicketPaymentDto.class).clientSecret()).isNull();
        assertThat(DtoJson.fromResource("dto/tickets/ticket-request-paid.json", TicketRequest.class).paymentIntentId()).isEqualTo("pi_3Pabc123");
        assertThat(new TicketRequest(1, 0, null).paymentIntentId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "dto/tickets/ticket-payment-stripe.json, aradexperience.dto.tickets.TicketPaymentDto",
            "dto/tickets/ticket-payment-demo.json, aradexperience.dto.tickets.TicketPaymentDto",
            "dto/tickets/ticket-request-paid.json, aradexperience.dto.tickets.TicketRequest",
    })
    void serialize_roundTrip_matchesJson(String path, Class<?> type) throws Exception {
        JSONAssert.assertEquals(DtoJson.read(path), DtoJson.APP.writeValueAsString(DtoJson.fromResource(path, type)), JSONCompareMode.STRICT);
    }
}
