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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Stripe REST (modul de test), cu serverul Stripe simulat de MockRestServiceServer. */
class StripeClientTest {

    static final StripeProps PROPS = new StripeProps("sk_test_123", "pk_test_456", "ron", "https://stripe.test");
    static final String PI = """
            {"id":"pi_1","client_secret":"pi_1_secret_x","status":"requires_payment_method","amount":10500,"currency":"ron",
             "metadata":{"eventId":"22","userId":"7","qtyAdult":"2","qtyChild":"1"}}""";

    MockRestServiceServer server;
    StripeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new StripeClient(PROPS, builder);
    }

    @Test
    void create_postsCardOnlyIntentWithMetadata_andBearerTestKey() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_123"))
                .andExpect(header("Idempotency-Key", "k1"))
                .andExpect(content().formDataContains(Map.of("amount", "10500", "currency", "ron", "payment_method_types[]", "card",
                        "metadata[eventId]", "22")))
                .andRespond(withSuccess(PI, MediaType.APPLICATION_JSON));

        StripeClient.Intent pi = client.create(10500, "AradExperience", Map.of("eventId", "22"), "k1");

        assertThat(pi.id()).isEqualTo("pi_1");
        assertThat(pi.clientSecret()).isEqualTo("pi_1_secret_x");
        assertThat(pi.amount()).isEqualTo(10500);
        assertThat(pi.metadata()).containsEntry("qtyChild", "1");
        server.verify();
    }

    @Test
    void retrieve_unknown_is400_providerDown_is502() {
        server.expect(requestTo("https://stripe.test/v1/payment_intents/pi_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.retrieve("pi_x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));

        server.reset();
        server.expect(requestTo("https://stripe.test/v1/payment_intents/pi_1")).andRespond(withServerError());
        assertThatThrownBy(() -> client.retrieve("pi_1")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e).hasMessage(StripeClient.UNAVAILABLE));
    }

    @Test
    void refundQuietly_postsRefund_andSwallowsErrors() {
        server.expect(requestTo("https://stripe.test/v1/refunds"))
                .andExpect(content().formDataContains(Map.of("payment_intent", "pi_1")))
                .andRespond(withServerError());
        client.refundQuietly("pi_1");
        server.verify();
    }
}
