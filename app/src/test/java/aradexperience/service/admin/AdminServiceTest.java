package aradexperience.service.admin;

import aradexperience.dto.admin.AdminUserUpdate;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.entity.users.User;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.enums.TicketStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.AdminMock;
import aradexperience.mock.BusinessMock;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.OfferMock;
import aradexperience.repository.admin.AdminContentRepository;
import aradexperience.repository.admin.AdminStatsRepository;
import aradexperience.repository.admin.AdminUserRepository;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.offers.OfferRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.service.points.PointsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock AdminStatsRepository stats;
    @Mock AdminContentRepository content;
    @Mock AdminUserRepository userSearch;
    @Mock EventRepository events;
    @Mock OfferRepository offers;
    @Mock CommunityActionRepository actions;
    @Mock UserRepository users;
    @Mock PointsService points;
    @InjectMocks AdminService service;

    // --- statistici --------------------------------------------------------------------------------------------------

    @Test
    void stats_fillsEveryRole_evenWithoutAccounts() {
        when(stats.usersByRole()).thenReturn(List.of(roleCount("LOCAL", 19), roleCount("ADMIN", 1)));
        when(stats.publishedEvents()).thenReturn(35L);
        when(stats.codesUsed()).thenReturn(3L);

        AdminService.Stats s = service.stats();

        assertThat(s.usersByRole()).hasSize(Role.values().length).containsEntry(Role.LOCAL, 19L).containsEntry(Role.VOLUNTEER, 0L);
        assertThat(s.events()).isEqualTo(35);
        assertThat(s.codesUsed()).isEqualTo(3);
    }

    // --- conținut & moderare -----------------------------------------------------------------------------------------

    @Test
    void content_events_filterByStatusAndTitle_andLoadsOrganizerBusinesses() {
        Event e = BusinessMock.event(24L, 1L);
        when(content.events(eq(List.of(ContentStatus.DRAFT)), eq("%vin%"), eq(false), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e)));
        when(content.businesses(List.of(1L))).thenReturn(List.of(BusinessMock.pasaj()));

        AdminService.Content c = service.content(AdminService.Kind.EVENTS, "draft", "  VIN ", false, 0, 500);

        assertThat(List.<Object>copyOf(c.page().getContent())).containsExactly(e);
        assertThat(c.businesses()).containsKey(1L);
    }

    @Test
    void content_actions_withoutStatus_meansAllStatuses() {
        when(content.actions(eq(List.of(ActionStatus.values())), eq("%%"), eq(true), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(CommunityMock.action(2L, 60, 14))));

        AdminService.Content c = service.content(AdminService.Kind.ACTIONS, null, null, true, 0, 50);

        assertThat(c.page().getContent()).hasSize(1);
        assertThat(c.businesses()).isEmpty();
    }

    @Test
    void content_unknownStatusOrKind_throws400() {
        assertThatThrownBy(() -> service.content(AdminService.Kind.OFFERS, "CANCELLED", null, false, 0, 10))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AdminService.Kind.parse("users")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void setStatus_hidesOffer_andPublishesAction() {
        Offer o = OfferMock.coffee();
        when(offers.findById(1L)).thenReturn(Optional.of(o));
        when(content.businesses(List.of(o.getBusinessId()))).thenReturn(List.of(BusinessMock.pasaj()));
        service.setStatus(AdminService.Kind.OFFERS, 1L, "HIDDEN", AdminMock.admin());
        assertThat(o.getStatus()).isEqualTo(ContentStatus.HIDDEN);

        CommunityAction a = CommunityMock.action(ActionStatus.DRAFT);
        when(actions.findById(a.getId())).thenReturn(Optional.of(a));
        service.setStatus(AdminService.Kind.ACTIONS, a.getId(), "PUBLISHED", AdminMock.admin());
        assertThat(a.getStatus()).isEqualTo(ActionStatus.PUBLISHED);
    }

    @Test
    void setStatus_actionCannotBeHidden_eventMustExist() {
        CommunityAction a = CommunityMock.action(ActionStatus.PUBLISHED);
        when(actions.findById(a.getId())).thenReturn(Optional.of(a));
        assertThatThrownBy(() -> service.setStatus(AdminService.Kind.ACTIONS, a.getId(), "HIDDEN", AdminMock.admin()))
                .isInstanceOf(BadRequestException.class);
        when(events.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.setStatus(AdminService.Kind.EVENTS, 404L, "HIDDEN", AdminMock.admin()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- utilizatori -------------------------------------------------------------------------------------------------

    @Test
    void users_byRole_withTicketCounts() {
        User u = AdminMock.local(111L);
        when(userSearch.search(eq(List.of(Role.LOCAL)), eq("%bog%"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(u)));
        when(userSearch.businessesOf(List.of(111L))).thenReturn(List.of());
        when(userSearch.ticketCounts(List.of(111L), TicketStatus.CANCELLED)).thenReturn(List.of(ticketCount(111L, 2)));

        List<AdminService.UserRow> rows = service.users(Role.LOCAL, "Bog", 0, 20).getContent();

        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.user()).isSameAs(u);
            assertThat(r.tickets()).isEqualTo(2);
        });
    }

    @Test
    void updateUser_changesRole_adjustsPointsWithNote_andDisables() {
        User u = AdminMock.local(111L);
        when(users.findById(111L)).thenReturn(Optional.of(u));

        AdminService.UserRow r = service.updateUser(111L, new AdminUserUpdate(Role.CITY_HALL, -20, " Corecție ", true), AdminMock.admin());

        assertThat(u.getRole()).isEqualTo(Role.CITY_HALL);
        assertThat(u.isDisabled()).isTrue();
        verify(points).adjust(111L, -20, "Corecție");
        assertThat(r.user()).isSameAs(u);
    }

    @Test
    void updateUser_enable_clearsDisabled_andDefaultNoteForPoints() {
        User u = AdminMock.local(111L);
        u.setDisabledAt(AdminMock.CREATED);
        when(users.findById(111L)).thenReturn(Optional.of(u));

        service.updateUser(111L, new AdminUserUpdate(null, 10, null, false), AdminMock.admin());

        assertThat(u.isDisabled()).isFalse();
        ArgumentCaptor<String> note = ArgumentCaptor.forClass(String.class);
        verify(points).adjust(eq(111L), eq(10), note.capture());
        assertThat(note.getValue()).isEqualTo("Admin adjustment");
    }

    @Test
    void updateUser_selfRoleOrDisable_volunteerAndBusinessRoles_throw400() {
        User me = UserMockAdmin();
        when(users.findById(AdminMock.ADMIN_ID)).thenReturn(Optional.of(me));
        assertThatThrownBy(() -> service.updateUser(AdminMock.ADMIN_ID, new AdminUserUpdate(Role.LOCAL, null, null, null), AdminMock.admin()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateUser(AdminMock.ADMIN_ID, new AdminUserUpdate(null, null, null, true), AdminMock.admin()))
                .isInstanceOf(BadRequestException.class);

        User local = AdminMock.local(111L);
        when(users.findById(111L)).thenReturn(Optional.of(local));
        assertThatThrownBy(() -> service.updateUser(111L, new AdminUserUpdate(Role.VOLUNTEER, null, null, null), AdminMock.admin()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateUser(111L, new AdminUserUpdate(Role.BUSINESS, null, null, null), AdminMock.admin()))
                .isInstanceOf(BadRequestException.class);
        assertThat(local.getRole()).isEqualTo(Role.LOCAL);
        verify(points, never()).adjust(anyLong(), anyInt(), anyString());
    }

    @Test
    void like_escapesWildcards() {
        assertThat(AdminService.like(" 50%_off ")).isEqualTo("%50off%");
        assertThat(AdminService.like(null)).isEqualTo("%%");
    }

    // --- ajutătoare --------------------------------------------------------------------------------------------------

    private static User UserMockAdmin() {
        return aradexperience.mock.UserMock.user(AdminMock.ADMIN_ID, "admin@demo.ro", Role.ADMIN);
    }

    private static AdminStatsRepository.RoleCount roleCount(String role, long total) {
        return new AdminStatsRepository.RoleCount() {
            public String getRole() { return role; }
            public Long getTotal() { return total; }
        };
    }

    private static AdminUserRepository.TicketCount ticketCount(long userId, long total) {
        return new AdminUserRepository.TicketCount() {
            public Long getUserId() { return userId; }
            public Long getTotal() { return total; }
        };
    }
}
