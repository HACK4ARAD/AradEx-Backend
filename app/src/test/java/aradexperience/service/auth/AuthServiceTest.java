package aradexperience.service.auth;

import aradexperience.dto.auth.UpdateMeRequest;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
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
    @InjectMocks AuthService service;

    @Test
    void register_local_savesNormalizedUserAndIssuesToken() {
        when(users.existsByEmail("ana@exemplu.ro")).thenReturn(false);
        when(passwordEncoder.encode(UserMock.PASSWORD)).thenReturn(UserMock.HASH);
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(42L); return u; });
        when(jwtService.issue(42L, "ana@exemplu.ro", Role.LOCAL)).thenReturn("jwt");

        AuthService.Session s = service.register(UserMock.register(Role.LOCAL));

        assertThat(s.token()).isEqualTo("jwt");
        assertThat(s.user().getEmail()).isEqualTo("ana@exemplu.ro");
        assertThat(s.user().getDisplayName()).isEqualTo("Ana Pop");
        assertThat(s.user().getPasswordHash()).isEqualTo(UserMock.HASH);
        assertThat(s.user().getLocale()).isEqualTo("ro");
        assertThat(s.business()).isNull();
        verify(businesses, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throws409() {
        when(users.existsByEmail("ana@exemplu.ro")).thenReturn(true);

        assertThatThrownBy(() -> service.register(UserMock.register(Role.VISITOR))).isInstanceOf(ConflictException.class);
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

        assertThatThrownBy(() -> service.register(UserMock.register(Role.LOCAL))).isInstanceOf(ConflictException.class);
    }

    @Test
    void register_businessDetailsWithNonBusinessRole_throws400() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        var req = UserMock.registerBusiness("CAFE");
        var asLocal = new aradexperience.dto.auth.RegisterRequest(req.email(), req.password(), req.displayName(), Role.LOCAL, req.business());

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
        User u = UserMock.local();
        when(users.findByEmail("local@demo.ro")).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(UserMock.PASSWORD, UserMock.HASH)).thenReturn(true);
        when(jwtService.issue(2L, "local@demo.ro", Role.LOCAL)).thenReturn("jwt");

        AuthService.Session s = service.login(UserMock.login(UserMock.PASSWORD));

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
}
