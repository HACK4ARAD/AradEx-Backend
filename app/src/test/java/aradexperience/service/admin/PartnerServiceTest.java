package aradexperience.service.admin;

import aradexperience.dto.admin.CityBusinessCreate;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AdminMock;
import aradexperience.repository.admin.AdminContentRepository;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static aradexperience.enums.ContentStatus.DRAFT;
import static aradexperience.enums.ContentStatus.PUBLISHED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PLAN.md T14 „Verificare”: firma nouă apare la verificat → verificată → conținutul ei (oferte, evenimente, anunțuri) devine public. */
@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    @Mock AdminContentRepository content;
    @Mock BusinessRepository businesses;
    @Mock UserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks PartnerService service;

    @Test
    void list_unverifiedOnly_withOwnerAndOfferCounts() {
        Business verified = AdminMock.bakery();
        verified.setId(1L);
        verified.setVerified(true);
        when(content.allBusinesses()).thenReturn(List.of(AdminMock.bakery(), verified));
        when(users.findAllById(List.of(12L))).thenReturn(List.of(AdminMock.bakeryOwner()));
        when(content.offerCounts(PUBLISHED, DRAFT)).thenReturn(List.of(AdminMock.counts(2L, 0, 1, 0)));

        List<PartnerService.Row> rows = service.list(false);

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.business().getName()).isEqualTo("Brutăria Veche");
            assertThat(r.owner().getEmail()).isEqualTo("brutarie@exemplu.ro");
            assertThat(r.draftOffers()).isEqualTo(1);
            assertThat(r.activeOffers()).isZero();
        });
    }

    @Test
    void verify_marksVerified_clearsRejection_andPublishesDrafts() {
        Business b = AdminMock.bakery();
        b.setRejectedAt(AdminMock.CREATED);
        b.setRejectReason("x");
        when(businesses.findById(2L)).thenReturn(Optional.of(b));
        when(content.moveOffers(2L, DRAFT, PUBLISHED)).thenReturn(1);
        when(users.findAllById(List.of(12L))).thenReturn(List.of(AdminMock.bakeryOwner()));
        when(content.offerCounts(PUBLISHED, DRAFT)).thenReturn(List.of(AdminMock.counts(2L, 1, 0, 0)));

        PartnerService.Row row = service.verify(2L, AdminMock.cityHall());

        assertThat(b.isVerified()).isTrue();
        assertThat(b.getRejectedAt()).isNull();
        assertThat(b.getRejectReason()).isNull();
        verify(content).moveEvents(2L, DRAFT, PUBLISHED);
        verify(content).moveOffers(2L, DRAFT, PUBLISHED);
        verify(content).moveAnnouncements(2L, DRAFT, PUBLISHED);
        assertThat(row.activeOffers()).isEqualTo(1);
    }

    @Test
    void reject_newBusiness_keepsContentAsDraft_andStoresReason() {
        Business b = AdminMock.bakery();
        when(businesses.findById(2L)).thenReturn(Optional.of(b));

        service.reject(2L, "  Date incomplete ", AdminMock.admin());

        assertThat(b.isVerified()).isFalse();
        assertThat(b.getRejectedAt()).isNotNull();
        assertThat(b.getRejectReason()).isEqualTo("Date incomplete");
        verify(content, never()).moveOffers(anyLong(), any(), any());
    }

    @Test
    void reject_verifiedBusiness_suspends_andPublishedContentBecomesDraft() {
        Business b = AdminMock.bakery();
        b.setVerified(true);
        when(businesses.findById(2L)).thenReturn(Optional.of(b));

        service.reject(2L, null, AdminMock.admin());

        assertThat(b.isVerified()).isFalse();
        assertThat(b.getRejectReason()).isNull();
        verify(content).moveEvents(2L, PUBLISHED, DRAFT);
        verify(content).moveOffers(2L, PUBLISHED, DRAFT);
        verify(content).moveAnnouncements(2L, PUBLISHED, DRAFT);
    }

    @Test
    void verify_unknown_throws404() {
        when(businesses.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify(99L, AdminMock.admin())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_newVerifiedBusinessAccount_withHashedOneTimePassword() {
        when(users.existsByEmail("ceainarie@exemplu.ro")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("$2a$hash");
        when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(40L); return u; });
        when(businesses.saveAndFlush(any(Business.class))).thenAnswer(inv -> { Business b = inv.getArgument(0); b.setId(7L); return b; });

        PartnerService.Created c = service.create(new CityBusinessCreate(" Ceainarie@Exemplu.ro ", "Ceainăria Nouă", BusinessCategory.CAFE,
                "Bd. Revoluției 10, Arad", "Ioana M.", " "), AdminMock.cityHall());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(user.capture());
        assertThat(user.getValue().getEmail()).isEqualTo("ceainarie@exemplu.ro");
        assertThat(user.getValue().getRole()).isEqualTo(Role.BUSINESS);
        assertThat(user.getValue().getPasswordHash()).isEqualTo("$2a$hash");
        assertThat(user.getValue().isMustChangePassword()).isTrue();
        assertThat(user.getValue().getDisplayName()).isEqualTo("Ioana M.");
        assertThat(c.row().business().isVerified()).isTrue();
        assertThat(c.row().business().getOwnerUserId()).isEqualTo(40L);
        assertThat(c.row().business().getLat()).isEqualTo(PartnerService.ARAD_LAT);
        assertThat(c.row().business().getPhone()).isNull();
        assertThat(c.invitePassword()).hasSize(12).doesNotContain("0", "O", "l", "1", "I");
    }

    @Test
    void create_existingEmail_throws409() {
        when(users.existsByEmail("firma@demo.ro")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new CityBusinessCreate("firma@demo.ro", "X", BusinessCategory.CAFE, "A", "B", null),
                AdminMock.admin())).isInstanceOf(ConflictException.class).hasMessage(PartnerService.EMAIL_TAKEN);
    }
}
