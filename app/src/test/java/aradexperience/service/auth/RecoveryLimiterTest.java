package aradexperience.service.auth;

import aradexperience.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecoveryLimiterTest {

    static final Instant T0 = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void fiveWrongAttemptsPerAddress_thenWaitAnHour_successClearsThem() {
        RecoveryLimiter l = new RecoveryLimiter();
        for (int i = 0; i < RecoveryLimiter.EMAIL_FAILURES; i++) {
            l.check("ana@exemplu.ro", null, T0.plusSeconds(i));
            l.failed("ana@exemplu.ro", null, T0.plusSeconds(i));
        }
        assertThatThrownBy(() -> l.check("ana@exemplu.ro", null, T0.plusSeconds(60)))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.retryAfterSeconds()).isEqualTo(3540));
        assertThatCode(() -> l.check("alta@exemplu.ro", null, T0.plusSeconds(60))).doesNotThrowAnyException();
        assertThatCode(() -> l.check("ana@exemplu.ro", null, T0.plus(Duration.ofMinutes(61)))).doesNotThrowAnyException();

        l.succeeded("ana@exemplu.ro");
        assertThatCode(() -> l.check("ana@exemplu.ro", null, T0.plusSeconds(60))).doesNotThrowAnyException();
    }

    @Test
    void oneNetwork_isCappedAcrossAddresses() {
        RecoveryLimiter l = new RecoveryLimiter();
        for (int i = 0; i < RecoveryLimiter.IP_FAILURES; i++) l.failed("om" + i + "@exemplu.ro", "10.0.0.7", T0.plusSeconds(i));

        assertThatThrownBy(() -> l.check("nou@exemplu.ro", "10.0.0.7", T0.plusSeconds(60))).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> l.check("nou@exemplu.ro", "10.0.0.8", T0.plusSeconds(60))).doesNotThrowAnyException();
    }
}
