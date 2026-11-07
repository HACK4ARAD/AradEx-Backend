package aradexperience.service.volunteer;

import aradexperience.dto.volunteer.MessageRequest;
import aradexperience.dto.volunteer.VolunteerCreate;
import aradexperience.dto.volunteer.VolunteerUpdate;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.entity.volunteer.VolunteerMessage;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.Role;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.VolunteerMock;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.VolunteerMessageRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.repository.volunteer.VolunteerScanRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityVolunteerServiceTest {

    @Mock VolunteerRepository volunteers;
    @Mock VolunteerMessageRepository messages;
    @Mock VolunteerScanRepository scans;
    @Mock UserRepository users;
    @Mock CommunityActionRepository actions;
    CityVolunteerService service;

    final CommunityAction published = VolunteerMock.actionAt(1L, CommunityMock.NOW.plusDays(1));
    final CommunityAction draft = VolunteerMock.actionAt(4L, CommunityMock.NOW.plusDays(2));
    final CommunityAction done = VolunteerMock.actionAt(5L, CommunityMock.NOW.minusDays(20));

    @BeforeEach
    void setUp() {
        service = new CityVolunteerService(volunteers, messages, scans, users, actions);
        service.clock = CommunityMock.CLOCK;
        draft.setStatus(ActionStatus.DRAFT);
        done.setStatus(ActionStatus.DONE);
        lenient().when(actions.findAllById(any())).thenAnswer(i -> {
            Iterable<Long> ids = i.getArgument(0);
            return java.util.stream.StreamSupport.stream(ids.spliterator(), false)
                    .map(id -> id == 1L ? published : id == 4L ? draft : id == 5L ? done : null).filter(java.util.Objects::nonNull).toList();
        });
        lenient().when(users.findById(4L)).thenReturn(Optional.of(VolunteerMock.user(4L, "Primăria Arad", Role.CITY_HALL)));
        lenient().when(users.findById(VolunteerMock.COORD_ID)).thenReturn(Optional.of(VolunteerMock.coordinator()));
        lenient().when(users.findAllById(any())).thenReturn(VolunteerMock.people());
        lenient().when(scans.scansBy(any())).thenReturn(List.of());
        lenient().when(messages.unreadByCity()).thenReturn(List.of());
        lenient().when(messages.lastPerVolunteer()).thenReturn(List.of());
    }

    @Test
    void create_volunteerAccount_withUniqueSixDigitCode_normalizedPhone_andTheCreatorAsDefaultCoordinator() {
        when(users.save(any())).thenAnswer(i -> { User u = i.getArgument(0); u.setId(VolunteerMock.VOL_ID); return u; });
        when(volunteers.existsByLoginCode(anyString())).thenReturn(true, false);
        when(volunteers.save(any())).thenAnswer(i -> i.getArgument(0));

        var view = service.create(new VolunteerCreate(" Andrei Popa ", "0740 000 001", " Andrei.Popa@Exemplu.ro ", null, List.of(1L), true),
                CommunityMock.cityHall());

        ArgumentCaptor<User> u = ArgumentCaptor.forClass(User.class);
        verify(users).save(u.capture());
        assertThat(u.getValue().getRole()).isEqualTo(Role.VOLUNTEER);
        assertThat(u.getValue().getDisplayName()).isEqualTo("Andrei Popa");
        assertThat(u.getValue().getPasswordHash()).isNull();
        assertThat(u.getValue().getPhone()).isNull();                 // telefonul nu stă în users: fără OTP pentru voluntari
        Volunteer v = view.volunteer();
        assertThat(v.getLoginCode()).matches("[1-9]\\d{5}");
        assertThat(v.getPhone()).isEqualTo("+40740000001");
        assertThat(v.getEmail()).isEqualTo("andrei.popa@exemplu.ro");
        assertThat(v.getCoordinatorId()).isEqualTo(4L);
        assertThat(v.getActionIds()).containsExactly(1L);
        assertThat(v.isOneTime()).isTrue();
        assertThat(v.getStatus()).isEqualTo(VolunteerStatus.ACTIVE);
    }

    @Test
    void create_rejectsUnpublishedOrUnknownActions_andACoordinatorWhoIsNotCityHall() {
        VolunteerCreate draftAction = new VolunteerCreate("Ana", "0740000003", null, null, List.of(4L), false);
        assertThatThrownBy(() -> service.create(draftAction, CommunityMock.cityHall())).isInstanceOf(BadRequestException.class).hasMessageContaining("not published");
        VolunteerCreate unknown = new VolunteerCreate("Ana", "0740000003", null, null, List.of(99L), false);
        assertThatThrownBy(() -> service.create(unknown, CommunityMock.cityHall())).isInstanceOf(BadRequestException.class).hasMessageContaining("Unknown action");
        when(users.findById(2L)).thenReturn(Optional.of(VolunteerMock.user(2L, "Ana Popescu", Role.LOCAL)));
        VolunteerCreate localCoordinator = new VolunteerCreate("Ana", "0740000003", null, 2L, List.of(), false);
        assertThatThrownBy(() -> service.create(localCoordinator, CommunityMock.cityHall())).isInstanceOf(BadRequestException.class).hasMessageContaining("coordinator");
        VolunteerCreate badPhone = new VolunteerCreate("Ana", "123", null, null, List.of(), false);
        assertThatThrownBy(() -> service.create(badPhone, CommunityMock.cityHall())).isInstanceOf(BadRequestException.class);
        verify(volunteers, never()).save(any());
    }

    @Test
    void update_suspends_reallocates_andKeepsAnAlreadyAllocatedFinishedAction() {
        Volunteer v = VolunteerMock.volunteer(5L);
        when(volunteers.findForUpdate(VolunteerMock.VOL_ID)).thenReturn(Optional.of(v));

        var view = service.update(VolunteerMock.VOL_ID, new VolunteerUpdate(List.of(5L, 1L), VolunteerStatus.SUSPENDED, null));
        assertThat(view.volunteer().getStatus()).isEqualTo(VolunteerStatus.SUSPENDED);
        assertThat(v.getActionIds()).containsExactly(5L, 1L);

        Volunteer other = VolunteerMock.volunteer();
        when(volunteers.findForUpdate(7L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.update(7L, new VolunteerUpdate(List.of(5L), null, null))).isInstanceOf(BadRequestException.class);
        when(volunteers.findForUpdate(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(8L, new VolunteerUpdate(null, VolunteerStatus.ACTIVE, null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void conversation_openingMarksTheVolunteersMessagesRead_andAReplyIsFromTheCity() {
        when(volunteers.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(VolunteerMock.volunteer(1L)));
        when(messages.findByVolunteerUserIdOrderByCreatedAtAscIdAsc(VolunteerMock.VOL_ID))
                .thenReturn(List.of(VolunteerMock.message(1, true, "Bună!"), VolunteerMock.message(2, false, "Mulțumesc!")));

        var thread = service.messages(VolunteerMock.VOL_ID);
        verify(messages).markRead(VolunteerMock.VOL_ID, false, CommunityMock.NOW);
        assertThat(thread).extracting(CityVolunteerService.MessageView::authorName).containsExactly("Ioana Marin", "Andrei Popa");

        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        var reply = service.send(VolunteerMock.VOL_ID, new MessageRequest("Poți începe scanarea"), CommunityMock.cityHall());
        ArgumentCaptor<VolunteerMessage> saved = ArgumentCaptor.forClass(VolunteerMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().isFromCity()).isTrue();
        assertThat(saved.getValue().getAuthorId()).isEqualTo(4L);
        assertThat(reply.authorName()).isEqualTo("Primăria Arad");
    }

    @Test
    void list_showsScans_unread_lastMessage_andExpiry() {
        Volunteer v = VolunteerMock.volunteer(5L);
        v.setOneTime(true);
        when(volunteers.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(v));
        when(scans.scansBy(any())).thenReturn(List.of(new VolunteerScanRepository.ScanCount() {
            public Long getUserId() { return VolunteerMock.VOL_ID; }
            public long getScans() { return 2; }
        }));
        when(messages.unreadByCity()).thenReturn(List.of(new VolunteerMessageRepository.UnreadRow() {
            public Long getVolunteerId() { return VolunteerMock.VOL_ID; }
            public long getUnread() { return 1; }
        }));
        when(messages.lastPerVolunteer()).thenReturn(List.of(VolunteerMock.message(9, false, "Am ajuns.")));

        var view = service.list().get(0);
        assertThat(view.scans()).isEqualTo(2);
        assertThat(view.unread()).isEqualTo(1);
        assertThat(view.lastMessage().message().getText()).isEqualTo("Am ajuns.");
        assertThat(view.lastMessage().authorName()).isEqualTo("Andrei Popa");
        assertThat(view.coordinator().getDisplayName()).isEqualTo("Ioana Marin");
        assertThat(view.expired()).isTrue();                          // unică folosință, singura acțiune s-a încheiat
    }
}
