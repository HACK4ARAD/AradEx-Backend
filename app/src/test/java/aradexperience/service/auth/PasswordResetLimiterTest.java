package aradexperience.service.auth;

import aradexperience.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetLimiterTest {

    static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void sameEmail_waitsAMinuteBetweenCodes_andGetsFiveADay() {
        PasswordResetLimiter l = new PasswordResetLimiter();
        l.acquire("ana@exemplu.ro", null, T0);

        assertThatThrownBy(() -> l.acquire("ana@exemplu.ro", null, T0.plusSeconds(30)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(30));
        for (int i = 1; i < PasswordResetLimiter.EMAIL_PER_DAY; i++) l.acquire("ana@exemplu.ro", null, T0.plus(Duration.ofMinutes(i * 2L)));
        assertThatThrownBy(() -> l.acquire("ana@exemplu.ro", null, T0.plus(Duration.ofHours(2)))).isInstanceOf(TooManyRequestsException.class);
        // a doua zi merge din nou
        assertThatCode(() -> l.acquire("ana@exemplu.ro", null, T0.plus(Duration.ofHours(25)))).doesNotThrowAnyException();
    }

    @Test
    void differentEmails_doNotBlockEachOther_butOneNetworkIsCapped() {
        PasswordResetLimiter l = new PasswordResetLimiter();
        for (int i = 0; i < PasswordResetLimiter.IP_SHORT; i++) l.acquire("om" + i + "@exemplu.ro", "10.0.0.7", T0.plusSeconds(i));

        assertThatThrownBy(() -> l.acquire("altcineva@exemplu.ro", "10.0.0.7", T0.plusSeconds(60))).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> l.acquire("altcineva@exemplu.ro", "10.0.0.8", T0.plusSeconds(60))).doesNotThrowAnyException();
        assertThatCode(() -> l.acquire("inca-unul@exemplu.ro", "10.0.0.7", T0.plus(Duration.ofMinutes(11)))).doesNotThrowAnyException();
    }

    @Test
    void aRejectedRequest_isNotCounted() {
        PasswordResetLimiter l = new PasswordResetLimiter();
        l.acquire("ana@exemplu.ro", "10.0.0.7", T0);
        assertThatThrownBy(() -> l.acquire("ana@exemplu.ro", "10.0.0.7", T0.plusSeconds(10))).isInstanceOf(TooManyRequestsException.class);

        assertThatCode(() -> l.acquire("ana@exemplu.ro", "10.0.0.7", T0.plusSeconds(61))).doesNotThrowAnyException();
    }
}
