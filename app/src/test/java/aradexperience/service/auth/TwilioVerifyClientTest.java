package aradexperience.service.auth;

import aradexperience.config.AppProps;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TwilioVerifyClientTest {

    static final AppProps.Twilio PROPS = new AppProps.Twilio("AC123", "secret", "VA456", "https://verify.test");
    static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("AC123:secret".getBytes(StandardCharsets.UTF_8));

    MockRestServiceServer server;
    TwilioVerifyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwilioVerifyClient(PROPS, builder);
    }

    @Test
    void start_postsVerificationWithBasicAuth() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", BASIC))
                .andExpect(content().formDataContains(java.util.Map.of("To", "+40744123456", "Channel", "sms")))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        client.start("+40744123456");

        server.verify();
    }

    @Test
    void check_trueOnlyWhenApproved() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/VerificationCheck"))
                .andExpect(content().formDataContains(java.util.Map.of("To", "+40744123456", "Code", "123456")))
                .andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://verify.test/v2/Services/VA456/VerificationCheck"))
                .andRespond(withSuccess("{\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        assertThat(client.check("+40744123456", "123456")).isTrue();
        assertThat(client.check("+40744123456", "000000")).isFalse();
    }

    @Test
    void check_notFound_meansExpiredOrUsed_false() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/VerificationCheck"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":20404}").contentType(MediaType.APPLICATION_JSON));

        assertThat(client.check("+40744123456", "123456")).isFalse();
    }

    @Test
    void start_rejectedNumber_throws400() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/Verifications"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":21608}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.start("+40744123456"))
                .isInstanceOf(BadRequestException.class).hasMessage("Phone number cannot receive SMS");
    }

    @Test
    void start_twilioRateLimit_throws429() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/Verifications"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.start("+40744123456")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void start_twilioDown_throws503() {
        server.expect(requestTo("https://verify.test/v2/Services/VA456/Verifications"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.start("+40744123456"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void notConfigured_throws503() {
        TwilioVerifyClient empty = new TwilioVerifyClient(new AppProps.Twilio("", "", "", null), RestClient.builder());

        assertThat(empty.configured()).isFalse();
        assertThatThrownBy(() -> empty.start("+40744123456"))
                .isInstanceOf(ApiException.class).hasMessage("SMS unavailable");
    }
}
