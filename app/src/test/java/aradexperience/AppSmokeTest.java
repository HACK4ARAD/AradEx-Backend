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
    void recoveryCode_fromSignUp_changesAForgottenPassword_andIsReplaced() {
        var req = new RegisterRequest("smoke.recover@exemplu.ro", "parola1234", "Smoke Recover", Role.LOCAL, null, true);
        AuthResponse created = rest.postForEntity("/api/v1/auth/register", req, AuthResponse.class).getBody();
        String code = created.recoveryCode();
        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(created.user().hasRecoveryCode()).isTrue();

        var wrong = java.util.Map.of("email", "smoke.recover@exemplu.ro", "recoveryCode", "2222-2222-2222", "password", "parola-noua-1");
        assertThat(rest.postForEntity("/api/v1/auth/password/recover", wrong, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        var right = java.util.Map.of("email", "Smoke.Recover@exemplu.ro", "recoveryCode", code.toLowerCase().replace("-", " "), "password", "parola-noua-1");
        ResponseEntity<AuthResponse> recovered = rest.postForEntity("/api/v1/auth/password/recover", right, AuthResponse.class);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().recoveryCode()).isNotEqualTo(code);
        // codul folosit nu mai merge; parola nouă da
        assertThat(rest.postForEntity("/api/v1/auth/password/recover", right, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/api/v1/auth/login", new LoginRequest("smoke.recover@exemplu.ro", "parola-noua-1"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminTemporaryPassword_forcesANewPassword_andGivesANewRecoveryCode() {
        var req = new RegisterRequest("smoke.temp@exemplu.ro", "parola1234", "Smoke Temp", Role.VISITOR, null, true);
        long id = rest.postForEntity("/api/v1/auth/register", req, AuthResponse.class).getBody().user().id();
        String admin = rest.postForEntity("/api/v1/auth/login", new LoginRequest("admin@demo.ro", "demo1234"), AuthResponse.class).getBody().token();

        ResponseEntity<java.util.Map> temp = rest.exchange("/api/v1/admin/users/" + id + "/temporary-password", HttpMethod.POST, bearer(admin), java.util.Map.class);
        assertThat(temp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String temporary = (String) temp.getBody().get("temporaryPassword");

        AuthResponse login = rest.postForEntity("/api/v1/auth/login", new LoginRequest("smoke.temp@exemplu.ro", temporary), AuthResponse.class).getBody();
        assertThat(login.user().mustChangePassword()).isTrue();
        assertThat(login.user().hasRecoveryCode()).isFalse();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(login.token());
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var change = new HttpEntity<>(java.util.Map.of("currentPassword", temporary, "newPassword", "parola-noua-2"), h);
        ResponseEntity<java.util.Map> changed = rest.exchange("/api/v1/me/password", HttpMethod.POST, change, java.util.Map.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) changed.getBody().get("recoveryCode")).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");

        UserDto me = rest.exchange("/api/v1/me", HttpMethod.GET, bearer(login.token()), UserDto.class).getBody();
        assertThat(me.mustChangePassword()).isFalse();
        assertThat(me.hasRecoveryCode()).isTrue();
    }

    @Test
    void demoAccounts_cannotGetATemporaryPassword_norChangeTheirs() {
        String admin = rest.postForEntity("/api/v1/auth/login", new LoginRequest("admin@demo.ro", "demo1234"), AuthResponse.class).getBody().token();
        assertThat(rest.exchange("/api/v1/admin/users/2/temporary-password", HttpMethod.POST, bearer(admin), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        AuthResponse local = rest.postForEntity("/api/v1/auth/login", new LoginRequest("local@demo.ro", "demo1234"), AuthResponse.class).getBody();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(local.token());
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var change = new HttpEntity<>(java.util.Map.of("currentPassword", "demo1234", "newPassword", "parola-noua-2"), h);
        assertThat(rest.exchange("/api/v1/me/password", HttpMethod.POST, change, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
