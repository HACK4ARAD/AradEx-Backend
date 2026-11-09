package aradexperience;

import aradexperience.dto.auth.AuthResponse;
import aradexperience.dto.auth.LoginRequest;
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

    // ---- conturile personale cu email + parolă și „Am uitat parola”, cap-coadă pe Postgres (V21) ----

    @Test
    void personalAccounts_signUpAndLogInWithEmail_andVisitorGets403OnCity() {
        var req = new RegisterRequest("smoke.visitor@exemplu.ro", "parola1234", "Smoke Visitor", Role.VISITOR, null, true);
        ResponseEntity<AuthResponse> created = rest.postForEntity("/api/v1/auth/register", req, AuthResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().user().role()).isEqualTo(Role.VISITOR);
        assertThat(rest.postForEntity("/api/v1/auth/register", req, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/v1/auth/login", new LoginRequest("Smoke.Visitor@exemplu.ro", "parola1234"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> city = rest.exchange("/api/v1/city/actions", HttpMethod.GET, bearer(login.getBody().token()), String.class);
        assertThat(city.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void demoLocal_logsInWithEmail() {
        ResponseEntity<AuthResponse> login = rest.postForEntity("/api/v1/auth/login", new LoginRequest("local@demo.ro", "demo1234"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        UserDto me = rest.exchange("/api/v1/me", HttpMethod.GET, bearer(login.getBody().token()), UserDto.class).getBody();
        assertThat(me.role()).isEqualTo(Role.LOCAL);
        assertThat(me.pointsBalance()).isEqualTo(270);
    }

    @Test
    void forgotPassword_isAlways204_andAWrongCodeIs401() {
        var forgot = java.util.Map.of("email", "nimeni@exemplu.ro");
        assertThat(rest.postForEntity("/api/v1/auth/password/forgot", forgot, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // același răspuns pentru un cont demo (nu primește cod) și pentru o adresă fără cont
        assertThat(rest.postForEntity("/api/v1/auth/password/forgot", java.util.Map.of("email", "local@demo.ro"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        // a doua cerere imediat după: 429, oricare ar fi adresa
        assertThat(rest.postForEntity("/api/v1/auth/password/forgot", forgot, String.class).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        var reset = java.util.Map.of("email", "local@demo.ro", "code", "123456", "password", "parola-noua-2026");
        assertThat(rest.postForEntity("/api/v1/auth/password/reset", reset, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // parola contului demo n-a fost atinsă
        assertThat(rest.postForEntity("/api/v1/auth/login", new LoginRequest("local@demo.ro", "demo1234"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void register_business_createsUnverifiedBusiness() {
        var req = new RegisterRequest("smoke.firma@exemplu.ro", "parola1234", "Ion Pop", Role.BUSINESS,
                new RegisterRequest.BusinessCreate("Ceainăria din Parc", "CAFE", "Str. Eminescu 5, Arad", null, null), true);
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
