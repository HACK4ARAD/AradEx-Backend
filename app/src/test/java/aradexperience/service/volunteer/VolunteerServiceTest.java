package aradexperience.service.volunteer;

import aradexperience.dto.volunteer.MessageRequest;
import aradexperience.dto.volunteer.VolunteerScanRequest;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.entity.volunteer.VolunteerMessage;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerActionState;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.exceptions.TooManyRequestsException;
import aradexperience.exceptions.UnauthorizedException;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.VolunteerMock;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerMessageRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.repository.volunteer.VolunteerScanRepository;
import aradexperience.security.JwtService;
import aradexperience.service.community.CityCommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolunteerServiceTest {

    @Mock VolunteerRepository volunteers;
    @Mock VolunteerMessageRepository messages;
    @Mock VolunteerScanRepository scans;
    @Mock UserRepository users;
    @Mock CommunityActionRepository actions;
    @Mock CommunityRegistrationRepository registrations;
    @Mock CityCommunityService cityCommunity;
    @Mock CityVolunteerService cityVolunteers;
    @Mock JwtService jwt;
    LoginAttempts attempts = new LoginAttempts();
    VolunteerService service;

    static final String CODE = "AX-C1-K4N7PA";
    /** 09:00; acțiunea 1 începe la 10:00 (deschisă de la 09:30), acțiunea 2 mâine. */
    final CommunityAction today = VolunteerMock.actionAt(1L, CommunityMock.NOW.plusHours(1));
    final CommunityAction tomorrow = VolunteerMock.actionAt(2L, CommunityMock.NOW.plusDays(1));
    Volunteer v;

    @BeforeEach
    void setUp() {
        service = new VolunteerService(volunteers, messages, scans, users, actions, registrations, cityCommunity, cityVolunteers, attempts, jwt);
        service.clock = CommunityMock.CLOCK;
        v = VolunteerMock.volunteer(1L, 2L);
        lenient().when(volunteers.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(v));
        lenient().when(actions.findAllById(any())).thenReturn(List.of(today, tomorrow));
        lenient().when(users.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(VolunteerMock.volunteerUser()));
        lenient().when(users.findById(VolunteerMock.COORD_ID)).thenReturn(Optional.of(VolunteerMock.coordinator()));
    }

    // ---- intrarea cu cod

    @Test
    void login_rightCode_issuesAVolunteerToken_andRemembersTheLogin() {
        when(volunteers.findByLoginCode("482913")).thenReturn(Optional.of(v));
        when(jwt.issue(VolunteerMock.VOL_ID, null, Role.VOLUNTEER)).thenReturn("jwt");
        var s = service.login(" 482913 ", "1.2.3.4");
        assertThat(s.token()).isEqualTo("jwt");
        assertThat(s.user().getRole()).isEqualTo(Role.VOLUNTEER);
        assertThat(v.getLastLoginAt()).isEqualTo(CommunityMock.NOW);
    }

    @Test
    void login_wrongCode_is401_andTooManyWrongCodesAre429() {
        when(volunteers.findByLoginCode(any())).thenReturn(Optional.empty());
        for (int i = 0; i < LoginAttempts.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> service.login("111111", "1.2.3.4")).isInstanceOf(UnauthorizedException.class);
        }
        assertThatThrownBy(() -> service.login("482913", "1.2.3.4")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void login_suspendedOrExpired_is401() {
        when(volunteers.findByLoginCode("482913")).thenReturn(Optional.of(v));
        v.setStatus(VolunteerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.login("482913", "k")).isInstanceOf(UnauthorizedException.class).hasMessageContaining("suspended");
        v.setStatus(VolunteerStatus.ACTIVE);
        v.setOneTime(true);
        when(actions.findAllById(any())).thenReturn(List.of(VolunteerMock.actionAt(1L, CommunityMock.NOW.minusDays(3))));
        assertThatThrownBy(() -> service.login("482913", "k")).isInstanceOf(UnauthorizedException.class).hasMessageContaining("expired");
        verify(jwt, never()).issue(anyLong(), any(), any());
    }

    @Test
    void accountDisabledByAdmin_cannotLogIn_norUseAnOldToken() {
        var u = VolunteerMock.volunteerUser();
        u.setDisabledAt(CommunityMock.NOW.minusHours(1));
        when(users.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(u));
        when(volunteers.findByLoginCode("482913")).thenReturn(Optional.of(v));
        assertThatThrownBy(() -> service.login("482913", "k")).isInstanceOf(ForbiddenException.class).hasMessage("Account disabled");
        assertThatThrownBy(() -> service.actions(VolunteerMock.current())).isInstanceOf(ForbiddenException.class).hasMessage("Account disabled");
        verify(jwt, never()).issue(anyLong(), any(), any());
    }

    // ---- acțiuni și antet

    @Test
    void actions_areSortedWithLockState_andMe_countsWhatIsLeftToScan() {
        today.setStartAt(CommunityMock.NOW.plusMinutes(20));     // deschisă (de la 08:50)
        today.setRegisteredCount(18);
        today.setConfirmedCount(3);
        tomorrow.setRegisteredCount(4);
        when(actions.findAllById(any())).thenReturn(List.of(tomorrow, today));
        when(scans.scansByActions(eq(VolunteerMock.VOL_ID), any())).thenReturn(List.of());

        var list = service.actions(VolunteerMock.current());
        assertThat(list).extracting(a -> a.action().getId()).containsExactly(1L, 2L);
        assertThat(list).extracting(VolunteerService.ActionView::state).containsExactly(VolunteerActionState.OPEN, VolunteerActionState.LOCKED);

        when(scans.scansBy(any())).thenReturn(List.of());
        when(messages.countByVolunteerUserIdAndFromCityAndReadAtIsNull(VolunteerMock.VOL_ID, true)).thenReturn(1);
        var me = service.me(VolunteerMock.current());
        assertThat(me.actions()).isEqualTo(2);
        assertThat(me.toScan()).isEqualTo(15 + 4);
        assertThat(me.coordinator().getDisplayName()).isEqualTo("Ioana Marin");
        assertThat(me.unread()).isEqualTo(1);
    }

    @Test
    void suspendedVolunteer_withAnOldToken_is403() {
        v.setStatus(VolunteerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.actions(VolunteerMock.current())).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest(CODE, null), VolunteerMock.current())).isInstanceOf(ForbiddenException.class);
        when(volunteers.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.me(VolunteerMock.current())).isInstanceOf(ForbiddenException.class);
    }

    // ---- scanare (verificarea din PLAN: voluntarul scanează QR-ul unui localnic → localnicul primește punctele)

    @Test
    void scan_openAllocatedAction_confirmsThroughTheCityConfirmation() {
        today.setStartAt(CommunityMock.NOW.plusMinutes(20));
        when(registrations.actionIdByCode(CODE)).thenReturn(Optional.of(1L));
        when(actions.findById(1L)).thenReturn(Optional.of(today));
        var view = mock(CityCommunityService.ConfirmView.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(cityCommunity.confirmByCode(CODE, VolunteerMock.current())).thenReturn(view);

        assertThat(service.scan(new VolunteerScanRequest(" ax-c1-k4n7pa ", 1L), VolunteerMock.current())).isSameAs(view);
        verify(cityCommunity).confirmByCode(CODE, VolunteerMock.current());
    }

    @Test
    void scan_unknown404_notAssigned403_otherAction409_locked403_ended403() {
        when(registrations.actionIdByCode(CODE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest(CODE, null), VolunteerMock.current())).isInstanceOf(NotFoundException.class);

        when(registrations.actionIdByCode("AX-C9-K4N7PA")).thenReturn(Optional.of(9L));
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest("AX-C9-K4N7PA", null), VolunteerMock.current()))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("not assigned");

        when(registrations.actionIdByCode("AX-C2-K4N7PA")).thenReturn(Optional.of(2L));
        when(actions.findById(2L)).thenReturn(Optional.of(tomorrow));
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest("AX-C2-K4N7PA", 1L), VolunteerMock.current()))
                .isInstanceOf(ConflictException.class).hasMessageContaining(tomorrow.getTitle());
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest("AX-C2-K4N7PA", 2L), VolunteerMock.current()))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("opens");

        tomorrow.setStartAt(CommunityMock.NOW.minusDays(2));
        tomorrow.setEndAt(CommunityMock.NOW.minusDays(2).plusHours(3));
        assertThatThrownBy(() -> service.scan(new VolunteerScanRequest("AX-C2-K4N7PA", null), VolunteerMock.current()))
                .isInstanceOf(ForbiddenException.class).hasMessageContaining("ended");
        verify(cityCommunity, never()).confirmByCode(any(), any());
    }

    // ---- mesaje

    @Test
    void messages_markCityMessagesRead_andSendingWritesAVolunteerMessage() {
        when(messages.findByVolunteerUserIdOrderByCreatedAtAscIdAsc(VolunteerMock.VOL_ID)).thenReturn(List.of(VolunteerMock.message(1, true, "Bună!")));
        service.messages(VolunteerMock.current());
        verify(messages).markRead(VolunteerMock.VOL_ID, true, CommunityMock.NOW);

        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        var sent = service.send(new MessageRequest("  Am ajuns la foișor.  "), VolunteerMock.current());
        ArgumentCaptor<VolunteerMessage> saved = ArgumentCaptor.forClass(VolunteerMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().isFromCity()).isFalse();
        assertThat(saved.getValue().getAuthorId()).isEqualTo(VolunteerMock.VOL_ID);
        assertThat(saved.getValue().getText()).isEqualTo("Am ajuns la foișor.");
        assertThat(sent.authorName()).isEqualTo("Andrei Popa");
    }
}
