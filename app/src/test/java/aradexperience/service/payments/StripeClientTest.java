package aradexperience.service.payments;

import aradexperience.exceptions.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class StripeClientTest {

    static final StripeProps TEST = new StripeProps("sk_test_123", "pk_test_456", "whsec_1", "https://stripe.test", "ron");
    static final String PI = """
            {"id":"pi_1","client_secret":"pi_1_secret_x","status":"requires_payment_method","amount":4000,"currency":"ron",
             "metadata":{"source":"aradexperience","eventId":"22","userId":"7"}}""";

    MockRestServiceServer server;
    StripeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StripeClient(TEST, builder);
    }

    @Test
    void createPaymentIntent_cardOnly_withMetadata_bearerKey_andIdempotencyKey() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_123"))
                .andExpect(header("Idempotency-Key", "checkout-7-abc"))
                .andExpect(content().formDataContains(Map.of("amount", "4000", "currency", "ron", "payment_method_types[]", "card",
                        "metadata[eventId]", "22", "metadata[source]", "aradexperience")))
                .andRespond(withSuccess(PI, MediaType.APPLICATION_JSON));

        StripeClient.PaymentIntent pi = client.createPaymentIntent(4000, "ron", "AradExperience · test",
                Map.of("eventId", "22", "source", "aradexperience"), "checkout-7-abc");

        assertThat(pi.id()).isEqualTo("pi_1");
        assertThat(pi.clientSecret()).isEqualTo("pi_1_secret_x");
        assertThat(pi.amount()).isEqualTo(4000);
        assertThat(pi.metadata()).containsEntry("userId", "7");
        server.verify();
    }

    @Test
    void retrieve_unknownIntent_is404_stripeDown_is503() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents/pi_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("https://stripe.test/v1/payment_intents/pi_y")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.retrieve("pi_x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.retrieve("pi_y"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void refund_postsThePaymentIntent() {
        server.expect(requestTo("https://stripe.test/v1/refunds"))
                .andExpect(content().formDataContains(Map.of("payment_intent", "pi_1")))
                .andRespond(withSuccess("{\"id\":\"re_1\",\"status\":\"succeeded\"}", MediaType.APPLICATION_JSON));
        client.refund("pi_1");
        server.verify();
    }

    @Test
    void liveOrMissingKeys_neverCallStripe() {
        StripeProps live = new StripeProps("sk_live_999", "pk_live_999", null, "https://stripe.test", null);
        assertThat(live.enabled()).isFalse();
        assertThat(live.liveKeyGiven()).isTrue();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer none = MockRestServiceServer.bindTo(builder).build();
        StripeClient off = new StripeClient(live, builder);
        assertThatThrownBy(() -> off.retrieve("pi_1"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        none.verify(); // nicio cerere spre Stripe
        assertThat(new StripeProps("sk_test_1", "pk_test_1", "", null, null).webhookConfigured()).isFalse();
        assertThat(new StripeProps("sk_test_1", "pk_test_1", null, null, null).currencyOrDefault()).isEqualTo("ron");
    }
}
