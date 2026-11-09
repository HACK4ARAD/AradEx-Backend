package aradexperience.service.auth;

import aradexperience.entity.users.User;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");
    static final String EMAIL = "ana@exemplu.ro";
    static final CurrentUser ADMIN = new CurrentUser(5L, "admin@demo.ro", Role.ADMIN);

    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RecoveryLimiter limiter;
    @InjectMocks PasswordService service;

    @BeforeEach
    void clock() {
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static User ana() {
        User u = new User();
        u.setId(42L);
        u.setEmail(EMAIL);
        u.setPasswordHash("pw-hash");
        u.setRecoveryCodeHash("code-hash");
        u.setDisplayName("Ana Pop");
        u.setRole(Role.LOCAL);
        return u;
    }

    // --- codul de recuperare --------------------------------------------------------------------------------------------

    @Test
    void issueRecoveryCode_storesOnlyTheHashOfTheNormalizedCode() {
        User u = ana();
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt:" + inv.getArgument(0));

        String code = service.issueRecoveryCode(u);

        assertThat(code).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(u.getRecoveryCodeHash()).isEqualTo("bcrypt:" + code.replace("-", ""));
    }

    @Test
    void recover_rightCodeInAnyForm_setsTheNewPassword_andClearsTheTemporaryFlag() {
        User u = ana();
        u.setMustChangePassword(true);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("K7QM4XPT9DWA", "code-hash")).thenReturn(true);
        when(passwordEncoder.encode("parola-noua")).thenReturn("new-hash");

        User r = service.recover(EMAIL, " k7qm-4xpt 9dwa", "parola-noua", "10.0.0.7");

        assertThat(r.getPasswordHash()).isEqualTo("new-hash");
        assertThat(r.isMustChangePassword()).isFalse();
        verify(limiter).check(EMAIL, "10.0.0.7", NOW);
        verify(limiter).succeeded(EMAIL);
        verify(users).save(u);
    }

    @Test
    void recover_wrongCode_is401_andCountsTheFailure() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ana()));
        when(passwordEncoder.matches("K7QM4XPT9DWA", "code-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.recover(EMAIL, "K7QM-4XPT-9DWA", "parola-noua", "10.0.0.7"))
                .isInstanceOf(UnauthorizedException.class).hasMessage(PasswordService.INVALID_RECOVERY);
        verify(limiter).failed(EMAIL, "10.0.0.7", NOW);
        verify(users, never()).save(any());
    }

    @Test
    void recover_unknownAddress_demoAccount_orAccountWithoutCode_isTheSame401_afterADummyBcrypt() {
        User demo = ana();
        demo.setEmail("local@demo.ro");
        User noCode = ana();
        noCode.setRecoveryCodeHash(null);
        when(users.findByEmail(anyString())).thenReturn(Optional.empty(), Optional.of(demo), Optional.of(noCode));

        for (String email : new String[]{"nimeni@exemplu.ro", "local@demo.ro", EMAIL}) {
            assertThatThrownBy(() -> service.recover(email, "K7QM-4XPT-9DWA", "parola-noua", null))
                    .isInstanceOf(UnauthorizedException.class).hasMessage(PasswordService.INVALID_RECOVERY);
        }
        verify(passwordEncoder, org.mockito.Mockito.times(3)).matches("K7QM4XPT9DWA", AuthService.DUMMY_HASH);
        verify(users, never()).save(any());
    }

    @Test
    void recover_malformedCode_is401_withoutMatchingAnything() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(ana()));

        assertThatThrownBy(() -> service.recover(EMAIL, "nu e un cod", "parola-noua", null)).isInstanceOf(UnauthorizedException.class);
        verify(passwordEncoder).matches("", "code-hash");
    }

    @Test
    void recover_disabledAccount_is403_overTheLimit_is429BeforeLookingAnythingUp() {
        User u = ana();
        u.setDisabledAt(OffsetDateTime.parse("2026-11-08T10:00:00Z"));
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("K7QM4XPT9DWA", "code-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.recover(EMAIL, "K7QM-4XPT-9DWA", "parola-noua", null)).isInstanceOf(ForbiddenException.class);

        doThrow(new TooManyRequestsException("Too many wrong attempts for this address", 60)).when(limiter).check("alta@exemplu.ro", null, NOW);
        assertThatThrownBy(() -> service.recover("alta@exemplu.ro", "K7QM-4XPT-9DWA", "parola-noua", null)).isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).findByEmail("alta@exemplu.ro");
    }

    // --- schimbarea parolei și codul nou din cont ------------------------------------------------------------------------

    @Test
    void changePassword_afterATemporaryPassword_givesANewRecoveryCode() {
        User u = ana();
        u.setMustChangePassword(true);
        u.setRecoveryCodeHash(null);
        when(passwordEncoder.matches("K7QM-4XPT-9DWA", "pw-hash")).thenReturn(true);
        when(passwordEncoder.matches("parola-noua", "pw-hash")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt:" + inv.getArgument(0));

        String code = service.changePassword(u, "K7QM-4XPT-9DWA", "parola-noua");

        assertThat(code).isNotNull();
        assertThat(u.getPasswordHash()).isEqualTo("bcrypt:parola-noua");
        assertThat(u.isMustChangePassword()).isFalse();
        assertThat(u.getRecoveryCodeHash()).isEqualTo("bcrypt:" + code.replace("-", ""));
        verify(users).save(u);
    }

    @Test
    void changePassword_normalCase_keepsTheRecoveryCode() {
        User u = ana();
        when(passwordEncoder.matches("veche", "pw-hash")).thenReturn(true);
        when(passwordEncoder.matches("parola-noua", "pw-hash")).thenReturn(false);
        when(passwordEncoder.encode("parola-noua")).thenReturn("new-hash");

        assertThat(service.changePassword(u, "veche", "parola-noua")).isNull();
        assertThat(u.getRecoveryCodeHash()).isEqualTo("code-hash");
    }

    @Test
    void changePassword_wrongCurrent_is400_sameAsBefore_is400_demo_is403() {
        User u = ana();
        when(passwordEncoder.matches("gresita", "pw-hash")).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(u, "gresita", "parola-noua")).isInstanceOf(BadRequestException.class).hasMessage(PasswordService.WRONG_PASSWORD);

        when(passwordEncoder.matches("veche", "pw-hash")).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(u, "veche", "veche")).isInstanceOf(BadRequestException.class).hasMessage(PasswordService.SAME_PASSWORD);

        User demo = ana();
        demo.setEmail("visitor@demo.ro");
        assertThatThrownBy(() -> service.changePassword(demo, "demo1234", "parola-noua")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.regenerateRecoveryCode(demo, "demo1234")).isInstanceOf(ForbiddenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void regenerateRecoveryCode_withThePassword_replacesTheOldOne() {
        User u = ana();
        when(passwordEncoder.matches("parola-mea", "pw-hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt:" + inv.getArgument(0));

        String code = service.regenerateRecoveryCode(u, "parola-mea");

        assertThat(u.getRecoveryCodeHash()).isEqualTo("bcrypt:" + code.replace("-", "")).isNotEqualTo("code-hash");
        verify(users).save(u);
    }

    // --- parola temporară de la Admin ------------------------------------------------------------------------------------

    @Test
    void temporaryPassword_setsItHashed_forcesAChange_andKillsTheOldRecoveryCode() {
        User u = ana();
        when(users.findById(42L)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "bcrypt:" + inv.getArgument(0));

        String temporary = service.temporaryPassword(42L, ADMIN);

        assertThat(temporary).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(u.getPasswordHash()).isEqualTo("bcrypt:" + temporary);
        assertThat(u.isMustChangePassword()).isTrue();
        assertThat(u.getRecoveryCodeHash()).isNull();
        verify(users).save(u);
    }

    @Test
    void temporaryPassword_isRefused_forAdminsVolunteersAccountsWithoutEmailAndDemo() {
        User admin = ana(); admin.setRole(Role.ADMIN);
        User volunteer = ana(); volunteer.setRole(Role.VOLUNTEER);
        User phoneOnly = ana(); phoneOnly.setEmail(null);
        User demo = ana(); demo.setEmail("firma@demo.ro");
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(volunteer));
        when(users.findById(3L)).thenReturn(Optional.of(phoneOnly));
        when(users.findById(4L)).thenReturn(Optional.of(demo));
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.temporaryPassword(1L, ADMIN)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.temporaryPassword(2L, ADMIN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.temporaryPassword(3L, ADMIN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.temporaryPassword(4L, ADMIN)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.temporaryPassword(99L, ADMIN)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(passwordEncoder);
        verify(users, never()).save(any());
    }
}
