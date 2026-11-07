package aradexperience.security;

import aradexperience.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123456";

    @Test
    void issue_thenParse_returnsSameUser() {
        JwtService jwt = new JwtService(SECRET, Duration.ofHours(1));
        String token = jwt.issue(42L, "ana@exemplu.ro", Role.LOCAL);

        Optional<CurrentUser> user = jwt.parse(token);

        assertThat(user).isPresent();
        assertThat(user.get().id()).isEqualTo(42L);
        assertThat(user.get().email()).isEqualTo("ana@exemplu.ro");
        assertThat(user.get().role()).isEqualTo(Role.LOCAL);
    }

    @Test
    void parse_expiredToken_returnsEmpty() {
        JwtService jwt = new JwtService(SECRET, Duration.ofSeconds(-10));
        String token = jwt.issue(1L, "x@y.ro", Role.VISITOR);

        assertThat(jwt.parse(token)).isEmpty();
    }

    @Test
    void parse_tokenSignedWithOtherSecret_returnsEmpty() {
        String token = new JwtService("another-secret-another-secret-another-1", Duration.ofHours(1)).issue(1L, "x@y.ro", Role.ADMIN);

        assertThat(new JwtService(SECRET, Duration.ofHours(1)).parse(token)).isEmpty();
    }

    @Test
    void parse_garbage_returnsEmpty() {
        assertThat(new JwtService(SECRET, Duration.ofHours(1)).parse("not-a-token")).isEmpty();
    }

    @Test
    void constructor_shortSecret_throws() {
        assertThatThrownBy(() -> new JwtService("short", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- T21: telefon + OTP -----------------------------------------------------------------------------------------

    @Test
    void issue_withoutEmail_parsesWithNullEmail() {
        JwtService jwt = new JwtService(SECRET, Duration.ofHours(1));

        Optional<CurrentUser> user = jwt.parse(jwt.issue(7L, null, Role.LOCAL));

        assertThat(user).isPresent();
        assertThat(user.get().email()).isNull();
        assertThat(user.get().role()).isEqualTo(Role.LOCAL);
    }

    @Test
    void signupToken_carriesThePhone_andIsNotAnAuthToken() {
        JwtService jwt = new JwtService(SECRET, Duration.ofHours(1));
        String signup = jwt.issueSignup("+40799000042");

        assertThat(jwt.parseSignup(signup)).contains("+40799000042");
        assertThat(jwt.parse(signup)).isEmpty();
        assertThat(jwt.parseSignup(jwt.issue(1L, "x@y.ro", Role.VISITOR))).isEmpty();
        assertThat(jwt.parseSignup("garbage")).isEmpty();
    }
}
