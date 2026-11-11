package aradexperience.service.auth;

import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.mock.UserMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock BusinessRepository businesses;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock PasswordService passwords;
    @Mock aradexperience.config.DemoMode demo;
    @InjectMocks AuthService service;

    @Test
    void register_personalRoles_createAccountWithEmailAndPassword() {
        for (Role r : new Role[]{Role.VISITOR, Role.LOCAL}) {
            when(users.existsByEmail("ana@exemplu.ro")).thenReturn(false);
            when(passwordEncoder.encode(UserMock.PASSWORD)).thenReturn(UserMock.HASH);
            when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(42L); return u; });
            when(jwtService.issue(42L, "ana@exemplu.ro", r)).thenReturn("jwt");
            when(passwords.issueRecoveryCode(any(User.class))).thenReturn("K7QM-4XPT-9DWA");

            AuthService.Session s = service.register(UserMock.register(r));

            assertThat(s.token()).isEqualTo("jwt");
            assertThat(s.recoveryCode()).isEqualTo("K7QM-4XPT-9DWA");
            assertThat(s.user().getEmail()).isEqualTo("ana@exemplu.ro");
            assertThat(s.user().getPasswordHash()).isEqualTo(UserMock.HASH);
            assertThat(s.user().getDisplayName()).isEqualTo("Ana Pop");
            assertThat(s.user().getRole()).isEqualTo(r);
            assertThat(s.user().getTermsVersion()).isEqualTo(AuthService.TERMS_VERSION);
            assertThat(s.business()).isNull();
        }
        verify(businesses, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throws409() {
        when(users.existsByEmail("firma.noua@exemplu.ro")).thenReturn(true);

        assertThatThrownBy(() -> service.register(UserMock.registerBusiness("CAFE"))).isInstanceOf(ConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_cityHallOrAdminOrVolunteer_throws400() {
        for (Role r : new Role[]{Role.CITY_HALL, Role.ADMIN, Role.VOLUNTEER}) {
            assertThatThrownBy(() -> service.register(UserMock.register(r))).isInstanceOf(BadRequestException.class);
        }
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_business_createsUnverifiedBusiness() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(UserMock.HASH);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(50L); return u; });
        when(businesses.save(any(Business.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthService.Session s = service.register(UserMock.registerBusiness("cafe"));

        ArgumentCaptor<Business> saved = ArgumentCaptor.forClass(Business.class);
        verify(businesses).save(saved.capture());
        assertThat(saved.getValue().getOwnerUserId()).isEqualTo(50L);
        assertThat(saved.getValue().getCategory()).isEqualTo(BusinessCategory.CAFE);
        assertThat(saved.getValue().isVerified()).isFalse();
        assertThat(s.business()).isSameAs(saved.getValue());
        assertThat(s.user().getTermsAcceptedAt()).isNotNull();
        assertThat(s.user().getTermsVersion()).isEqualTo(AuthService.TERMS_VERSION);
    }

    @Test
    void register_businessWithoutDetails_throws400() {
        when(users.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.register(UserMock.register(Role.BUSINESS))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void register_businessWithUnknownCategory_throws400() {
        when(users.existsByEmail(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.register(UserMock.registerBusiness("CASINO"))).isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrentDuplicate_hitsUniqueConstraint_throws409() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(UserMock.HASH);
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("users_email_key"));

        assertThatThrownBy(() -> service.register(UserMock.registerBusiness("CAFE"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void register_businessDetailsWithNonBusinessRole_throws400() {
        var req = UserMock.registerBusiness("CAFE");
        var asLocal = new aradexperience.dto.auth.RegisterRequest(req.email(), req.password(), req.displayName(), Role.LOCAL, req.business(), true);

        assertThatThrownBy(() -> service.register(asLocal)).isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void login_unknownEmail_stillRunsBcrypt() {
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(UserMock.login(UserMock.PASSWORD))).isInstanceOf(UnauthorizedException.class);
        verify(passwordEncoder).matches(UserMock.PASSWORD, AuthService.DUMMY_HASH);
    }

    @Test
    void normalize_trimsAndLowercases() {
        assertThat(AuthService.normalize("  Ana@Exemplu.RO ")).isEqualTo("ana@exemplu.ro");
    }

    @Test
    void login_ok_returnsTokenAndUser() {
        User u = UserMock.businessOwner();
        when(users.findByEmail("firma@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, UserMock.HASH)).thenReturn(true);
        when(jwtService.issue(3L, "firma@demo.ro", Role.BUSINESS)).thenReturn("jwt");

        AuthService.Session s = service.login(new aradexperience.dto.auth.LoginRequest("firma@demo.ro", UserMock.PASSWORD));

        assertThat(s.token()).isEqualTo("jwt");
        assertThat(s.user()).isSameAs(u);
    }

    @Test
    void login_wrongPassword_throws401() {
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(UserMock.local()));
        when(passwordEncoder.matches("gresita123", UserMock.HASH)).thenReturn(false);

        assertThatThrownBy(() -> service.login(UserMock.login("gresita123")))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.INVALID_CREDENTIALS);
    }

    @Test
    void login_unknownEmail_throws401WithSameMessage() {
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(UserMock.login(UserMock.PASSWORD)))
                .isInstanceOf(UnauthorizedException.class).hasMessage(AuthService.INVALID_CREDENTIALS);
    }

    @Test
    void me_business_includesBusiness() {
        User owner = UserMock.businessOwner();
        Business b = UserMock.business(owner.getId());
        when(users.findById(3L)).thenReturn(Optional.of(owner));
        when(businesses.findByOwnerUserId(3L)).thenReturn(Optional.of(b));

        AuthService.Profile p = service.me(UserMock.current(owner));

        assertThat(p.business()).isSameAs(b);
    }

    @Test
    void me_deletedAccount_throws401() {
        when(users.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(UserMock.current(UserMock.local()))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateMe_changesNameAndLocale() {
        User u = UserMock.local();
        when(users.findById(2L)).thenReturn(Optional.of(u));

        service.updateMe(UserMock.current(u), new UpdateMeRequest("  Ana P. ", "en"));

        assertThat(u.getDisplayName()).isEqualTo("Ana P.");
        assertThat(u.getLocale()).isEqualTo("en");
    }

    @Test
    void updateMe_blankName_throws400() {
        User u = UserMock.local();
        when(users.findById(2L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.updateMe(UserMock.current(u), new UpdateMeRequest("   ", null))).isInstanceOf(BadRequestException.class);
    }

    // --- email + parolă pentru conturile personale, „Am uitat parola” ---------------------------------------------------

    @Test
    void login_personalAccount_rightPassword_returnsSession() {
        User u = UserMock.local();
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, UserMock.HASH)).thenReturn(true);
        when(jwtService.issue(u.getId(), u.getEmail(), Role.LOCAL)).thenReturn("jwt");

        AuthService.Session s = service.login(UserMock.login(UserMock.PASSWORD));

        assertThat(s.token()).isEqualTo("jwt");
        assertThat(s.user()).isSameAs(u);
    }

    @Test
    void login_phoneOnlyAccount_withoutPassword_throws401() {
        User u = UserMock.local();
        u.setPasswordHash(null);
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, AuthService.DUMMY_HASH)).thenReturn(false); // fără parolă: tot BCrypt, pe hash-ul fals

        assertThatThrownBy(() -> service.login(UserMock.login(UserMock.PASSWORD))).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void register_demoDomain_throws400() {
        var req = new aradexperience.dto.auth.RegisterRequest("cineva@demo.ro", UserMock.PASSWORD, "Ana", Role.VISITOR, null, true);

        assertThatThrownBy(() -> service.register(req)).isInstanceOf(BadRequestException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void recoverPassword_normalizesEmail_andReturnsSessionWithANewCode() {
        User u = UserMock.local();
        when(passwords.recover("ana@exemplu.ro", "k7qm 4xpt 9dwa", "parola-noua", "10.0.0.7")).thenReturn(u);
        when(passwords.issueRecoveryCode(u)).thenReturn("M3RT-8KQW-2ZPX");
        when(jwtService.issue(u.getId(), u.getEmail(), Role.LOCAL)).thenReturn("jwt");

        AuthService.Session s = service.recoverPassword("  Ana@Exemplu.RO ", "k7qm 4xpt 9dwa", "parola-noua", "10.0.0.7");

        assertThat(s.token()).isEqualTo("jwt");
        assertThat(s.user()).isSameAs(u);
        assertThat(s.recoveryCode()).isEqualTo("M3RT-8KQW-2ZPX");
        verify(users).save(u);
    }

    @Test
    void changePasswordAndNewRecoveryCode_loadTheCurrentAccount() {
        User u = UserMock.local();
        when(users.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwords.changePassword(u, "veche", "parola-noua")).thenReturn(null);
        when(passwords.regenerateRecoveryCode(u, "parola-noua")).thenReturn("M3RT-8KQW-2ZPX");

        assertThat(service.changePassword(UserMock.current(u), "veche", "parola-noua")).isNull();
        assertThat(service.newRecoveryCode(UserMock.current(u), "parola-noua")).isEqualTo("M3RT-8KQW-2ZPX");
    }

    // --- T14: conturi dezactivate de Admin --------------------------------------------------------------------------

    @Test
    void login_disabledAccount_rightPassword_throws403() {
        User u = UserMock.businessOwner();
        u.setDisabledAt(java.time.OffsetDateTime.parse("2026-11-07T10:00:00Z"));
        when(users.findByEmail("firma@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, UserMock.HASH)).thenReturn(true);

        assertThatThrownBy(() -> service.login(new aradexperience.dto.auth.LoginRequest("firma@demo.ro", UserMock.PASSWORD)))
                .isInstanceOf(ForbiddenException.class).hasMessage(AuthService.ACCOUNT_DISABLED);
    }

    // --- „doar demo” până la lansare -----------------------------------------------------------------------------------

    @Test
    void demoOnly_blocksRealAccountsBeforeThePassword_andNewAccounts() {
        AuthService s = new AuthService(users, businesses, passwordEncoder, jwtService, passwords,
                new aradexperience.config.DemoMode(new aradexperience.config.AppProps(null, null, null, null, true)));

        assertThatThrownBy(() -> s.login(new aradexperience.dto.auth.LoginRequest("Ana@Exemplu.ro", "orice-parola")))
                .isInstanceOf(ForbiddenException.class).hasMessage(aradexperience.config.DemoMode.NOT_LIVE);
        assertThatThrownBy(() -> s.register(UserMock.register(Role.VISITOR))).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> s.recoverPassword("ana@exemplu.ro", "K7QM-4XPT-9DWA", "parola-noua", null)).isInstanceOf(ForbiddenException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(users, never()).saveAndFlush(any());
        assertThat(s.demoOnly()).isTrue();
    }

    @Test
    void demoOnly_stillLetsTheDemoAccountsIn() {
        AuthService s = new AuthService(users, businesses, passwordEncoder, jwtService, passwords,
                new aradexperience.config.DemoMode(new aradexperience.config.AppProps(null, null, null, null, true)));
        User u = UserMock.local();
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, UserMock.HASH)).thenReturn(true);
        when(jwtService.issue(u.getId(), u.getEmail(), Role.LOCAL)).thenReturn("jwt");

        assertThat(s.login(UserMock.login(UserMock.PASSWORD)).token()).isEqualTo("jwt");
    }
}
