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
    void fortySmsPerTenMinutes_aWholeRoomOnOneWifi_thenWaitUntilTheOldestLeavesTheWindow() {
        // ~40 de oameni la un eveniment, pe același Wi-Fi (aceeași adresă publică), își fac cont în câteva minute
        for (int i = 0; i < 40; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plusSeconds(10); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isEqualTo(200));
        assertThatCode(() -> limiter.check("5.6.7.8", t)).doesNotThrowAnyException();
    }

    @Test
    void hundredSmsPerDay() {
        for (int i = 0; i < 100; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plus(Duration.ofMinutes(10)); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t)).isInstanceOf(TooManyRequestsException.class);
        t = t.plus(Duration.ofHours(8));
        assertThatCode(() -> limiter.check("1.2.3.4", t)).doesNotThrowAnyException();
    }

    @Test
    void unknownClientIsNotLimitedHere() {
        for (int i = 0; i < 20; i++) { limiter.check(null, t); limiter.record(null, t); }
        assertThatCode(() -> limiter.check(null, t)).doesNotThrowAnyException();
    }
}
