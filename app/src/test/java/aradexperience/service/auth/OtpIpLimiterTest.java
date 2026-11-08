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
    void twentySmsPerTenMinutes_aWholeRoomOnOneWifi_thenWaitUntilTheOldestLeavesTheWindow() {
        // ~20 oameni la un eveniment, pe același Wi-Fi (aceeași adresă publică), își fac cont în câteva minute
        for (int i = 0; i < 20; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plusSeconds(20); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isEqualTo(200));
        assertThatCode(() -> limiter.check("5.6.7.8", t)).doesNotThrowAnyException();
    }

    @Test
    void sixtySmsPerDay() {
        for (int i = 0; i < 60; i++) { limiter.check("1.2.3.4", t); limiter.record("1.2.3.4", t); t = t.plus(Duration.ofMinutes(15)); }
        assertThatThrownBy(() -> limiter.check("1.2.3.4", t)).isInstanceOf(TooManyRequestsException.class);
        t = t.plus(Duration.ofHours(10));
        assertThatCode(() -> limiter.check("1.2.3.4", t)).doesNotThrowAnyException();
    }

    @Test
    void unknownClientIsNotLimitedHere() {
        for (int i = 0; i < 20; i++) { limiter.check(null, t); limiter.record(null, t); }
        assertThatCode(() -> limiter.check(null, t)).doesNotThrowAnyException();
    }
}
