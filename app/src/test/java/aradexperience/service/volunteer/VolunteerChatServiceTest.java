package aradexperience.service.volunteer;

import aradexperience.dto.media.MediaDto;
import aradexperience.entity.volunteer.ActionChatMessage;
import aradexperience.entity.volunteer.ActionChatRead;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.enums.VolunteerStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.MediaMock;
import aradexperience.mock.VolunteerMock;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.ActionChatMessageRepository;
import aradexperience.repository.volunteer.ActionChatReadRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.service.media.MediaService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VolunteerChatServiceTest {

    @Mock ActionChatMessageRepository messages;
    @Mock ActionChatReadRepository reads;
    @Mock VolunteerRepository volunteers;
    @Mock CommunityActionRepository actions;
    @Mock UserRepository users;
    @Mock MediaService media;
    VolunteerChatService service;
    Volunteer v;

    @BeforeEach
    void setUp() {
        VolunteerAccess access = new VolunteerAccess(volunteers, users, actions);
        access.clock = CommunityMock.CLOCK;
        service = new VolunteerChatService(messages, reads, volunteers, actions, users, media, access);
        v = VolunteerMock.volunteer(1L);
        lenient().when(volunteers.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(v));
        lenient().when(users.findById(VolunteerMock.VOL_ID)).thenReturn(Optional.of(VolunteerMock.volunteerUser()));
        lenient().when(actions.findById(1L)).thenReturn(Optional.of(VolunteerMock.actionAt(1L, CommunityMock.NOW.plusHours(1))));
        lenient().when(actions.findAllById(any())).thenReturn(List.of(VolunteerMock.actionAt(1L, CommunityMock.NOW.plusHours(1))));
        lenient().when(users.findAllById(any())).thenReturn(List.of(VolunteerMock.volunteerUser(), VolunteerMock.otherVolunteerUser()));
        lenient().when(reads.findById(any())).thenReturn(Optional.empty());
        lenient().when(messages.save(any())).thenAnswer(i -> { ActionChatMessage m = i.getArgument(0); m.setId(10L); return m; });
    }

    @Test
    void chat_isOnlyForTheActionsVolunteers() {
        assertThatThrownBy(() -> service.chat(2L, VolunteerMock.current())).isInstanceOf(ForbiddenException.class).hasMessageContaining("not assigned");
        assertThatThrownBy(() -> service.post(2L, "Bună", VolunteerMock.current())).isInstanceOf(ForbiddenException.class);
        v.setStatus(VolunteerStatus.SUSPENDED);
        assertThatThrownBy(() -> service.chat(1L, VolunteerMock.current())).isInstanceOf(ForbiddenException.class).hasMessageContaining("suspended");
        verify(messages, never()).save(any());
    }

    @Test
    void openingTheChat_returnsMembersAndMessagesInOrder_andMarksItRead() {
        when(messages.findByActionIdOrderByIdDesc(eq(1L), any())).thenReturn(List.of(
                VolunteerMock.chat(3, 1, VolunteerMock.OTHER_VOL_ID, null, "/img/actiune-plantare-copaci.jpg"),
                VolunteerMock.chat(2, 1, VolunteerMock.OTHER_VOL_ID, "Iau sacii.", null),
                VolunteerMock.chat(1, 1, VolunteerMock.VOL_ID, "Ajung la 09:15.", null)));
        when(volunteers.findByAction(1L)).thenReturn(List.of(v, VolunteerMock.volunteer(1L)));

        var chat = service.chat(1L, VolunteerMock.current());

        assertThat(chat.messages()).extracting(m -> m.message().getId()).containsExactly(1L, 2L, 3L);
        assertThat(chat.messages()).extracting(VolunteerChatService.MessageView::authorName).containsExactly("Andrei Popa", "Maria Ionescu", "Maria Ionescu");
        assertThat(chat.members()).extracting(u -> u.getDisplayName()).containsExactly("Andrei Popa", "Maria Ionescu");
        ArgumentCaptor<ActionChatRead> read = ArgumentCaptor.forClass(ActionChatRead.class);
        verify(reads).save(read.capture());
        assertThat(read.getValue().getLastReadId()).isEqualTo(3L);
    }

    @Test
    void post_text_andPhotoGoesToTheChatFolder() {
        var msg = service.post(1L, "  Am ajuns.  ", VolunteerMock.current());
        assertThat(msg.message().getText()).isEqualTo("Am ajuns.");
        assertThat(msg.message().getAuthorId()).isEqualTo(VolunteerMock.VOL_ID);
        assertThat(msg.authorName()).isEqualTo("Andrei Popa");
        assertThatThrownBy(() -> service.post(1L, "   ", VolunteerMock.current())).isInstanceOf(BadRequestException.class);

        when(media.upload(MediaMock.JPEG, "chat")).thenReturn(new MediaDto("https://api.test/api/v1/media/chat/ab.jpg", "chat/ab.jpg", "image/jpeg", 13));
        var photo = service.postPhoto(1L, MediaMock.JPEG, " ", VolunteerMock.current());
        assertThat(photo.message().getImageUrl()).isEqualTo("https://api.test/api/v1/media/chat/ab.jpg");
        assertThat(photo.message().getText()).isNull();
        verify(media).upload(MediaMock.JPEG, "chat");
    }

    @Test
    void chats_listUnreadFirst_andUnreadTotalForTheTabDot() {
        var tomorrow = VolunteerMock.actionAt(2L, CommunityMock.NOW.plusDays(1));
        v.getActionIds().add(2L);
        when(actions.findAllById(any())).thenReturn(List.of(VolunteerMock.actionAt(1L, CommunityMock.NOW.plusHours(1)), tomorrow));
        when(volunteers.members(any())).thenReturn(List.of(members(1L, 2), members(2L, 1)));
        when(messages.lastPerAction(any())).thenReturn(List.of(VolunteerMock.chat(4, 1, VolunteerMock.VOL_ID, "Super.", null),
                VolunteerMock.chat(2, 2, VolunteerMock.OTHER_VOL_ID, "Mâine la 9?", null)));
        when(messages.unread(eq(VolunteerMock.VOL_ID), any())).thenReturn(List.of(unread(2L, 1)));

        var list = service.chats(VolunteerMock.current());
        assertThat(list).extracting(s -> s.action().getId()).containsExactly(2L, 1L);   // cel cu mesaje noi întâi
        assertThat(list.get(0).unread()).isEqualTo(1);
        assertThat(list.get(1).members()).isEqualTo(2);
        assertThat(service.unreadTotal(v)).isEqualTo(1);
    }

    private static VolunteerRepository.MembersRow members(long actionId, long n) {
        return new VolunteerRepository.MembersRow() {
            public Long getActionId() { return actionId; }
            public long getMembers() { return n; }
        };
    }

    private static ActionChatMessageRepository.UnreadRow unread(long actionId, long n) {
        return new ActionChatMessageRepository.UnreadRow() {
            public Long getActionId() { return actionId; }
            public long getUnread() { return n; }
        };
    }
}
