package aradexperience.service.auth;

import aradexperience.config.AppProps;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.TooManyRequestsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Twilio Verify (T21): trimite SMS-ul cu codul și îl verifică. Twilio generează și ține codul; noi nu îl vedem.
 * Prin RestClient (fără SDK). Erorile Twilio devin 400 (număr care nu poate primi SMS), 429 sau 503, niciodată 500.
 */
@Slf4j
@Component
public class TwilioVerifyClient {

    static final String UNAVAILABLE = "SMS unavailable";
    /** Twilio limitează și el trimiterile pe număr; aplicația arată atunci o pauză de un minut. */
    static final long TWILIO_RETRY_SECONDS = 60;

    private final AppProps.Twilio props;
    private final RestClient http;

    public TwilioVerifyClient(AppProps props, RestClient.Builder builder) {
        this(props.twilio(), builder);
    }

    TwilioVerifyClient(AppProps.Twilio props, RestClient.Builder builder) {
        this.props = props;
        String base = props.baseUrl() == null || props.baseUrl().isBlank() ? "https://verify.twilio.com" : props.baseUrl();
        this.http = builder.baseUrl(base).defaultHeaders(h -> {
            if (props.configured()) h.setBasicAuth(props.accountSid(), props.authToken());
        }).build();
    }

    public boolean configured() {
        return props.configured();
    }

    /** Trimite SMS-ul cu codul către numărul E.164. */
    public void start(String e164) {
        post("/v2/Services/{sid}/Verifications", form("To", e164, "Channel", "sms"));
    }

    /** `true` doar dacă Twilio confirmă codul (`approved`); cod greșit, expirat sau deja folosit → `false`. */
    public boolean check(String e164, String code) {
        try {
            Map<?, ?> body = post("/v2/Services/{sid}/VerificationCheck", form("To", e164, "Code", code));
            return body != null && "approved".equals(body.get("status"));
        } catch (BadRequestException e) {
            return false; // de ex. format de cod respins de Twilio
        }
    }

    private Map<?, ?> post(String path, MultiValueMap<String, String> form) {
        if (!configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        try {
            return http.post().uri(path, props.verifyServiceSid())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) return Map.of("status", "not_found"); // verificare expirată / folosită
            if (status == 429) throw new TooManyRequestsException("Too many codes requested", TWILIO_RETRY_SECONDS);
            if (status >= 400 && status < 500) {
                log.warn("Twilio Verify rejected the request: HTTP {}", status);
                throw new BadRequestException("Phone number cannot receive SMS");
            }
            log.error("Twilio Verify failed: HTTP {}", status);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Twilio Verify unreachable: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }
    }

    private static MultiValueMap<String, String> form(String k1, String v1, String k2, String v2) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add(k1, v1);
        f.add(k2, v2);
        return f;
    }
}
