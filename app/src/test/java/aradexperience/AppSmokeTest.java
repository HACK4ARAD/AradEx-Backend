package aradexperience;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.OtpRegisterRequest;
import aradexperience.dto.auth.OtpRequest;
import aradexperience.dto.auth.OtpRequestResponse;
import aradexperience.dto.auth.OtpVerifyRequest;
import aradexperience.dto.auth.OtpVerifyResponse;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.dto.auth.UserDto;
import aradexperience.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de fum: aplicația pornește pe un Postgres real (Testcontainers), Flyway rulează, /health răspunde,
 * rutele protejate dau 401 fără token. Se sare automat dacă nu există Docker pe mașină.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppSmokeTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    /** HttpURLConnection (implicit) nu poate citi un 401 pe POST („cannot retry due to server authentication”); clientul java.net.http poate. */
    private static final JdkClientHttpRequestFactory JDK_CLIENT = new JdkClientHttpRequestFactory();

    @BeforeEach
    void useJdkHttpClient() {
        if (rest.getRestTemplate().getRequestFactory() != JDK_CLIENT) rest.getRestTemplate().setRequestFactory(JDK_CLIENT);
    }

    @Test
    void health_isOk() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void protectedRoute_withoutToken_is401() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/me", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminRoute_withoutToken_is401() {
        ResponseEntity<String> res = rest.getForEntity("/api/v1/admin/stats", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- T03: fluxul de auth cap-coadă, pe conturile din V2_1__seed_users.sql ----

    @Test
    void demoAccounts_canLogIn_andMeReturnsThem() {
        for (String email : new String[]{"firma@demo.ro", "primaria@demo.ro", "admin@demo.ro"}) {
            ResponseEntity<AuthResponse> login = rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, "demo1234"), AuthResponse.class);
            assertThat(login.getStatusCode()).as(email).isEqualTo(HttpStatus.OK);

            ResponseEntity<UserDto> me = rest.exchange("/api/v1/me", HttpMethod.GET, bearer(login.getBody().token()), UserDto.class);
            assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(me.getBody().email()).isEqualTo(email);
        }
    }

    @Test
    void firmaDemo_hasVerifiedBusiness() {
        UserDto user = rest.postForEntity("/api/v1/auth/login", new LoginRequest("firma@demo.ro", "demo1234"), AuthResponse.class).getBody().user();
        assertThat(user.role()).isEqualTo(Role.BUSINESS);
        assertThat(user.business().name()).isEqualTo("Cafeneaua din Pasaj");
        assertThat(user.business().verified()).isTrue();
    }

    @Test
    void wrongPassword_is401() {
        ResponseEntity<String> res = rest.postForEntity("/api/v1/auth/login", new LoginRequest("local@demo.ro", "gresita123"), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void personalAccounts_cannotUseEmail_anymore() {
        // T21: Vizitatorii și Localnicii intră doar cu telefon + cod
        for (String email : new String[]{"visitor@demo.ro", "local@demo.ro"}) {
            ResponseEntity<String> login = rest.postForEntity("/api/v1/auth/login", new LoginRequest(email, "demo1234"), String.class);
            assertThat(login.getStatusCode()).as(email).isEqualTo(HttpStatus.FORBIDDEN);
        }
        var req = new RegisterRequest("smoke.visitor@exemplu.ro", "parola1234", "Smoke Visitor", Role.VISITOR, null);
        assertThat(rest.postForEntity("/api/v1/auth/register", req, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- T21: telefon + cod OTP, cap-coadă pe Postgres (V11) ----

    @Test
    void demoPhone_existingAccount_logsInWithTheCodeFromTheResponse() {
        String code = rest.postForEntity("/api/v1/auth/otp/request", new OtpRequest("0799 000 002"), OtpRequestResponse.class).getBody().demoCode();

        ResponseEntity<OtpVerifyResponse> verified = rest.postForEntity("/api/v1/auth/otp/verify", new OtpVerifyRequest("0799000002", code), OtpVerifyResponse.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getBody().signupToken()).isNull();

        UserDto me = rest.exchange("/api/v1/me", HttpMethod.GET, bearer(verified.getBody().token()), UserDto.class).getBody();
        assertThat(me.phone()).isEqualTo("+40799000002");
        assertThat(me.role()).isEqualTo(Role.LOCAL);
        assertThat(me.pointsBalance()).isEqualTo(270);
    }

    @Test
    void demoPhone_newNumber_signsUp_thenDuplicate_is409_andVisitorGets403OnCity() {
        String code = rest.postForEntity("/api/v1/auth/otp/request", new OtpRequest("0799000500"), OtpRequestResponse.class).getBody().demoCode();
        String signup = rest.postForEntity("/api/v1/auth/otp/verify", new OtpVerifyRequest("0799000500", code), OtpVerifyResponse.class).getBody().signupToken();
        assertThat(signup).isNotBlank();

        // tokenul de înregistrare nu e autentificare
        assertThat(rest.exchange("/api/v1/me", HttpMethod.GET, bearer(signup), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var req = new OtpRegisterRequest(signup, "Smoke Visitor", Role.VISITOR);
        ResponseEntity<AuthResponse> created = rest.postForEntity("/api/v1/auth/otp/register", req, AuthResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().user().phone()).isEqualTo("+40799000500");

        assertThat(rest.postForEntity("/api/v1/auth/otp/register", req, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> city = rest.exchange("/api/v1/city/actions", HttpMethod.GET, bearer(created.getBody().token()), String.class);
        assertThat(city.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void realPhone_withoutTwilioKeys_is503() {
        assertThat(rest.postForEntity("/api/v1/auth/otp/request", new OtpRequest("0744123456"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void register_business_createsUnverifiedBusiness() {
        var req = new RegisterRequest("smoke.firma@exemplu.ro", "parola1234", "Ion Pop", Role.BUSINESS,
                new RegisterRequest.BusinessCreate("Ceainăria din Parc", "CAFE", "Str. Eminescu 5, Arad", null, null));
        ResponseEntity<AuthResponse> res = rest.postForEntity("/api/v1/auth/register", req, AuthResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().user().business().verified()).isFalse();
    }

    private static HttpEntity<Void> bearer(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return new HttpEntity<>(h);
    }
}
