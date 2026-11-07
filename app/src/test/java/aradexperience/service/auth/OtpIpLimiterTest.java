package aradexperience.service.auth;

import aradexperience.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class OtpIpLimiterTest {

    final OtpIpLimiter limiter = new OtpIpLimiter();
    Instant t = Instant.parse("2026-11-07T12:00:00Z");

    @Test
    void threeSmsPerTenMinutes_thenWaitUntilTheOldestLeavesTheWindow() {
        for (int i = 0; i < 3; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plusSeconds(60); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isEqualTo(Duration.ofMinutes(7).toSeconds()));
        assertThatCode(() -> limiter.check("5.6.7.8", t)).doesNotThrowAnyException();
    }

    @Test
    void tenSmsPerDay() {
        for (int i = 0; i < 10; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plus(Duration.ofMinutes(30)); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t)).isInstanceOf(TooManyRequestsException.class);
        t = t.plus(Duration.ofHours(20));
        assertThatCode(() -> limiter.check("1.2.3.4", t)).doesNotThrowAnyException();
    }

    @Test
    void unknownClientIsNotLimitedHere() {
        for (int i = 0; i < 20; i++) { limiter.check(null, t); limiter.record(null, t); }
        assertThatCode(() -> limiter.check(null, t)).doesNotThrowAnyException();
    }
}
