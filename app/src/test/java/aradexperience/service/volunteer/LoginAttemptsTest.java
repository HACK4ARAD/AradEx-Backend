package aradexperience.service.volunteer;

import aradexperience.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptsTest {

    @Test
    void tenWrongCodes_block_untilTheWindowPasses_andSuccessResets() {
        LoginAttempts attempts = new LoginAttempts();
        Instant t0 = Instant.parse("2026-11-07T08:00:00Z");
        attempts.clock = Clock.fixed(t0, ZoneOffset.UTC);
        for (int i = 0; i < LoginAttempts.MAX_FAILURES; i++) {
            assertThatCode(() -> attempts.check("1.2.3.4")).doesNotThrowAnyException();
            attempts.failed("1.2.3.4");
        }
        assertThatThrownBy(() -> attempts.check("1.2.3.4")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> attempts.check("5.6.7.8")).doesNotThrowAnyException();   // altă adresă

        attempts.clock = Clock.fixed(t0.plus(LoginAttempts.WINDOW).plus(Duration.ofSeconds(1)), ZoneOffset.UTC);
        assertThatCode(() -> attempts.check("1.2.3.4")).doesNotThrowAnyException();

        attempts.failed("9.9.9.9");
        attempts.succeeded("9.9.9.9");
        assertThatCode(() -> attempts.check("9.9.9.9")).doesNotThrowAnyException();
    }
}
