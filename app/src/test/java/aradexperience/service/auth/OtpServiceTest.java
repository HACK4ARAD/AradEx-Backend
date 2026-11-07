package aradexperience.service.auth;

import aradexperience.entity.auth.OtpCode;
import aradexperience.exceptions.ApiException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.OtpCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    static final String DEMO = "+40799000001";
    static final String REAL = "+40744123456";

    final List<OtpCode> rows = new ArrayList<>();
    OtpCodeRepository repo;
    TwilioVerifyClient twilio;
    OtpService service;
    Instant now = Instant.parse("2026-11-07T12:00:00Z");

    @BeforeEach
    void setUp() {
        repo = mock(OtpCodeRepository.class);
        when(repo.save(any(OtpCode.class))).thenAnswer(inv -> { OtpCode c = inv.getArgument(0); if (!rows.contains(c)) rows.add(c); return c; });
        when(repo.findFirstByPhoneOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0))).max(Comparator.comparing(OtpCode::getCreatedAt)));
        when(repo.countByPhoneAndCreatedAtAfter(anyString(), any(Instant.class))).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0)) && c.getCreatedAt().isAfter(inv.getArgument(1))).count());
        twilio = mock(TwilioVerifyClient.class);
        service = new OtpService(repo, new BCryptPasswordEncoder(4), twilio);
        at(now);
    }

    void at(Instant t) {
        now = t;
        service.setClock(Clock.fixed(t, ZoneOffset.UTC));
    }

    void later(Duration d) {
        at(now.plus(d));
    }

    @Test
    void demo_requestReturnsCode_verifyAcceptsItOnce() {
        OtpService.RequestResult r = service.request("0799 000 001");

        assertThat(r.demoCode()).matches("\\d{6}");
        assertThat(r.retryAfterSeconds()).isEqualTo(60);
        assertThat(rows.get(0).getCodeHash()).isNotEqualTo(r.demoCode());
        verify(twilio, never()).start(anyString());

        assertThat(service.verify("+40799000001", r.demoCode())).isEqualTo(DEMO);
        assertThatThrownBy(() -> service.verify(DEMO, r.demoCode())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void secondRequestWithin60s_throws429WithWait() {
        service.request(DEMO);
        later(Duration.ofSeconds(20));

        assertThatThrownBy(() -> service.request(DEMO))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isBetween(1L, 60L).isEqualTo(40L));
    }

    @Test
    void sixthRequestInADay_throws429() {
        for (int i = 0; i < 5; i++) { service.request(DEMO); later(Duration.ofMinutes(2)); }

        assertThatThrownBy(() -> service.request(DEMO)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void expiredCode_throws401() {
        String code = service.request(DEMO).demoCode();
        later(Duration.ofMinutes(5).plusSeconds(1));

        assertThatThrownBy(() -> service.verify(DEMO, code)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void fiveWrongAttempts_thenEvenTheRightCodeFails() {
        String code = service.request(DEMO).demoCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.verify(DEMO, wrong)).isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> service.verify(DEMO, code)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verifyWithoutRequest_throws401() {
        assertThatThrownBy(() -> service.verify(DEMO, "123456")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void realNumber_usesTwilio_noDemoCode() {
        when(twilio.check(REAL, "654321")).thenReturn(true);

        OtpService.RequestResult r = service.request("0744 123 456");

        assertThat(r.demoCode()).isNull();
        verify(twilio).start(REAL);
        assertThat(service.verify(REAL, "654321")).isEqualTo(REAL);
    }

    @Test
    void realNumber_wrongCode_throws401() {
        when(twilio.check(REAL, "000000")).thenReturn(false);
        service.request(REAL);

        assertThatThrownBy(() -> service.verify(REAL, "000000")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void twilioNotConfigured_throws503_andNothingIsSaved() {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS unavailable")).when(twilio).start(REAL);

        assertThatThrownBy(() -> service.request(REAL))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(rows).isEmpty();
    }

    @Test
    void codeWithWrongShape_throws401_withoutCallingTwilio() {
        service.request(REAL);

        assertThatThrownBy(() -> service.verify(REAL, "12ab")).isInstanceOf(UnauthorizedException.class);
        verify(twilio, never()).check(anyString(), anyString());
    }
}
