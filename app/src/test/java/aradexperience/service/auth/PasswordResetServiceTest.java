package aradexperience.service.auth;

import aradexperience.entity.auth.PasswordResetCode;
import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.auth.PasswordResetCodeRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.service.mail.MailOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    static final String EMAIL = "ana@exemplu.ro";

    @Mock UserRepository users;
    @Mock PasswordResetCodeRepository codes;
    @Mock PasswordEncoder passwordEncoder;
    @Mock MailOutbox mail;
    @Mock PasswordResetLimiter limiter;
    @InjectMocks PasswordResetService service;

    @BeforeEach
    void clock() {
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static User ana() {
        User u = new User();
        u.setId(42L);
        u.setEmail(EMAIL);
        u.setPasswordHash("old-hash");
        u.setDisplayName("Ana Pop");
        u.setRole(Role.LOCAL);
        u.setLocale("ro");
        return u;
    }

    static PasswordResetCode pending(long id, String hash, int attempts) {
        PasswordResetCode c = new PasswordResetCode();
        c.setId(id);
        c.setUserId(42L);
        c.setCodeHash(hash);
        c.setAttempts(attempts);
        c.setCreatedAt(NOW.minusSeconds(120));
        c.setExpiresAt(NOW.plusSeconds(780));
        return c;
    }

    // --- cererea de cod ---------------------------------------------------------------------------------------------

    @Test
    void request_existingAccount_savesOnlyTheHash_andEmailsTheCodeInTheAccountLanguage() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ana()));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));

        service.request(EMAIL, "10.0.0.7");

        verify(limiter).acquire(EMAIL, "10.0.0.7", NOW);
        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(codes).save(saved.capture());
        String code = saved.getValue().getCodeHash().substring("hash:".length());
        assertThat(code).matches("\\d{6}");
        assertThat(saved.getValue().getUserId()).isEqualTo(42L);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(PasswordResetService.TTL));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).sendAfterCommit(eq(EMAIL), eq(PasswordResetMail.subject("ro")), body.capture());
        assertThat(body.getValue()).contains(code).contains("Ana Pop").contains("15 minute");
    }

    @Test
    void request_addressWithoutAccount_stillHashesACode_butSavesAndSendsNothing() {
        when(users.findByEmail("nimeni@exemplu.ro")).thenReturn(Optional.empty());

        service.request("nimeni@exemplu.ro", "10.0.0.7");

        verify(passwordEncoder).encode(anyString());
        verify(codes, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void request_demoDisabledOrVolunteerAccounts_getNoCode() {
        User demo = ana();
        demo.setEmail("local@demo.ro");
        User disabled = ana();
        disabled.setDisabledAt(OffsetDateTime.parse("2026-11-08T10:00:00Z"));
        User volunteer = ana();
        volunteer.setRole(Role.VOLUNTEER);
        when(users.findByEmail(anyString())).thenReturn(Optional.of(demo), Optional.of(disabled), Optional.of(volunteer));

        service.request("local@demo.ro", null);
        service.request(EMAIL, null);
        service.request(EMAIL, null);

        verify(codes, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void request_overTheLimit_is429_beforeLookingUpTheAccount() {
        doThrow(new TooManyRequestsException("Wait before requesting a new code", 30)).when(limiter).acquire(EMAIL, "10.0.0.7", NOW);

        assertThatThrownBy(() -> service.request(EMAIL, "10.0.0.7")).isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(users, codes, mail);
    }

    // --- resetarea --------------------------------------------------------------------------------------------------

    @Test
    void reset_rightCode_setsTheNewPassword_andClosesEveryOpenCode() {
        User u = ana();
        PasswordResetCode older = pending(1, "h1", 0);
        PasswordResetCode newer = pending(2, "h2", 1);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(u));
        when(codes.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(42L, NOW)).thenReturn(List.of(newer, older));
        when(passwordEncoder.matches("111111", "h2")).thenReturn(false);
        when(passwordEncoder.matches("111111", "h1")).thenReturn(true); // codul din primul email, ajuns mai târziu
        when(passwordEncoder.encode("parola-noua")).thenReturn("new-hash");

        User result = service.reset(EMAIL, "111111", "parola-noua");

        assertThat(result.getPasswordHash()).isEqualTo("new-hash");
        verify(users).save(u);
        assertThat(older.getConsumedAt()).isEqualTo(NOW);
        assertThat(newer.getConsumedAt()).isEqualTo(NOW);
    }

    @Test
    void reset_wrongCode_is401_andCountsTheAttemptOnEveryOpenCode() {
        PasswordResetCode a = pending(1, "h1", 0);
        PasswordResetCode b = pending(2, "h2", 3);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ana()));
        when(codes.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(42L, NOW)).thenReturn(List.of(b, a));

        assertThatThrownBy(() -> service.reset(EMAIL, "000000", "parola-noua"))
                .isInstanceOf(UnauthorizedException.class).hasMessage(PasswordResetService.INVALID_CODE);
        assertThat(a.getAttempts()).isEqualTo(1);
        assertThat(b.getAttempts()).isEqualTo(4);
        verify(users, never()).save(any());
    }

    @Test
    void reset_codeWithFiveWrongAttempts_isDead_even_withTheRightCode() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ana()));
        when(codes.findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(42L, NOW))
                .thenReturn(List.of(pending(1, "h1", PasswordResetService.MAX_ATTEMPTS)));

        assertThatThrownBy(() -> service.reset(EMAIL, "111111", "parola-noua")).isInstanceOf(UnauthorizedException.class);
        verify(passwordEncoder).matches("111111", AuthService.DUMMY_HASH);
        verify(users, never()).save(any());
    }

    @Test
    void reset_unknownOrDemoAddress_is401_withTheSameMessage() {
        User demo = ana();
        demo.setEmail("local@demo.ro");
        when(users.findByEmail("nimeni@exemplu.ro")).thenReturn(Optional.empty());
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(demo));

        assertThatThrownBy(() -> service.reset("nimeni@exemplu.ro", "111111", "parola-noua")).hasMessage(PasswordResetService.INVALID_CODE);
        assertThatThrownBy(() -> service.reset("local@demo.ro", "111111", "parola-noua")).hasMessage(PasswordResetService.INVALID_CODE);
        verifyNoInteractions(codes);
        verify(users, never()).save(any());
    }
}
