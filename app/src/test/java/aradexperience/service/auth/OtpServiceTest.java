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
    long extraTwilioToday = 0;

    @BeforeEach
    void setUp() {
        repo = mock(OtpCodeRepository.class);
        when(repo.save(any(OtpCode.class))).thenAnswer(inv -> { OtpCode c = inv.getArgument(0); if (!rows.contains(c)) rows.add(c); return c; });
        when(repo.findFirstByPhoneOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0))).max(Comparator.comparing(OtpCode::getCreatedAt)));
        when(repo.countByPhoneAndCreatedAtAfter(anyString(), any(Instant.class))).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0)) && c.getCreatedAt().isAfter(inv.getArgument(1))).count());
        when(repo.findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(anyString(), any(Instant.class))).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0)) && c.getCreatedAt().isAfter(inv.getArgument(1))).min(Comparator.comparing(OtpCode::getCreatedAt)));
        when(repo.findTop10ByPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(anyString(), any(Instant.class))).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getPhone().equals(inv.getArgument(0)) && c.getConsumedAt() == null && c.getExpiresAt().isAfter(inv.getArgument(1)))
                .sorted(Comparator.comparing(OtpCode::getCreatedAt).reversed()).limit(10).toList());
        when(repo.countByProviderAndCreatedAtAfter(any(OtpCode.Provider.class), any(Instant.class))).thenAnswer(inv -> rows.stream()
                .filter(c -> c.getProvider() == inv.getArgument(0) && c.getCreatedAt().isAfter(inv.getArgument(1))).count() + extraTwilioToday);
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
        service.request(REAL);
        later(Duration.ofSeconds(20));

        assertThatThrownBy(() -> service.request(REAL))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isBetween(1L, 60L).isEqualTo(40L));
    }

    @Test
    void sixthRequestInADay_throws429_untilTheOldestLeavesTheWindow() {
        for (int i = 0; i < 5; i++) { service.request(REAL); later(Duration.ofMinutes(2)); }

        // primul cod a fost cerut acum 10 minute → se poate cere iar peste 24 h − 10 min
        assertThatThrownBy(() -> service.request(REAL))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(e -> assertThat(((TooManyRequestsException) e).retryAfterSeconds()).isEqualTo(Duration.ofHours(24).minusMinutes(10).toSeconds()));
    }

    @Test
    void demoNumbers_haveNoCooldownOrDailyLimit_soTheSharedDemoAccountsCannotBeLocked() {
        for (int i = 0; i < 8; i++) assertThat(service.request(DEMO).demoCode()).matches("\\d{6}");
    }

    @Test
    void demo_twoPeopleAtOnce_bothCodesWork() {
        String first = service.request(DEMO).demoCode();
        String second = service.request(DEMO).demoCode();

        assertThat(service.verify(DEMO, first)).isEqualTo(DEMO);
        assertThat(service.verify(DEMO, second)).isEqualTo(DEMO);
    }

    @Test
    void realSms_onlyToRomanianNumbers() {
        assertThatThrownBy(() -> service.request("+4915112345678"))
                .isInstanceOf(aradexperience.exceptions.BadRequestException.class);
        verify(twilio, never()).start(anyString());
    }

    @Test
    void realSms_globalDailyCap_throws503() {
        extraTwilioToday = OtpService.GLOBAL_DAILY_SMS;

        assertThatThrownBy(() -> service.request(REAL))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(twilio, never()).start(anyString());
    }

    @Test
    void requestAndVerify_lockThePhone_soParallelCallsAreSerialized() {
        when(twilio.check(REAL, "123456")).thenReturn(true);
        service.request(REAL);
        service.verify(REAL, "123456");

        verify(repo, org.mockito.Mockito.times(2)).lockPhone(REAL);
    }

    @Test
    void expiredCode_throws401() {
        String code = service.request(DEMO).demoCode();
        later(Duration.ofMinutes(5).plusSeconds(1));

        assertThatThrownBy(() -> service.verify(DEMO, code)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void fiveWrongAttempts_thenEvenTheRightCodeFails() {
        when(twilio.check(REAL, "000000")).thenReturn(false);
        when(twilio.check(REAL, "123456")).thenReturn(true);
        service.request(REAL);
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.verify(REAL, "000000")).isInstanceOf(UnauthorizedException.class);

        assertThatThrownBy(() -> service.verify(REAL, "123456")).isInstanceOf(UnauthorizedException.class);
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
