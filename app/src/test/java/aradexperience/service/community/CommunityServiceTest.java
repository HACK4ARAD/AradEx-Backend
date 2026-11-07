package aradexperience.service.community;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.RegistrationStatus;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.CommunityMock;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock CommunityActionRepository actions;
    @Mock CommunityRegistrationRepository registrations;
    @Mock UserRepository users;
    @InjectMocks CommunityService service;

    @BeforeEach
    void fixedClock() {
        service.clock = CommunityMock.CLOCK;
    }

    @Test
    void register_createsCodeAndTakesASpot() {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(registrations.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        CommunityService.RegistrationView v = service.register(1L, CommunityMock.local());

        assertThat(v.registration().getCode()).matches("^AX-C1-[A-HJ-NP-Z2-9]{6}$");
        assertThat(v.registration().getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
        assertThat(a.getRegisteredCount()).isEqualTo(19);
    }

    @Test
    void register_full_is409() {
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(CommunityMock.action(1L, 40, 40)));
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(1L, CommunityMock.local())).isInstanceOf(ConflictException.class).hasMessageContaining("full");
        verify(registrations, never()).saveAndFlush(any());
    }

    @Test
    void register_twice_is409() {
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(CommunityMock.action(1L, 40, 18)));
        when(registrations.findByActionIdAndUserId(1L, 2L))
                .thenReturn(Optional.of(CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.REGISTERED)));

        assertThatThrownBy(() -> service.register(1L, CommunityMock.local())).isInstanceOf(ConflictException.class).hasMessageContaining("already registered");
    }

    @Test
    void register_concurrentDuplicate_hitsUniqueIndex_is409() {
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(CommunityMock.action(1L, 40, 18)));
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        when(registrations.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.register(1L, CommunityMock.local())).isInstanceOf(ConflictException.class);
    }

    @Test
    void register_afterCancel_reusesSameRowAndCode() {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        CommunityRegistration cancelled = CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.CANCELLED);
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.of(cancelled));
        when(registrations.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        CommunityService.RegistrationView v = service.register(1L, CommunityMock.local());

        assertThat(v.registration().getId()).isEqualTo(5L);
        assertThat(v.registration().getCode()).isEqualTo("AX-C1-K4N7PA");
        assertThat(v.registration().getStatus()).isEqualTo(RegistrationStatus.REGISTERED);
    }

    @Test
    void register_endedOrNotPublished_is409_draftIs404() {
        CommunityAction ended = CommunityMock.action(1L, 40, 18);
        ended.setStartAt(CommunityMock.NOW.minusHours(5));
        ended.setEndAt(CommunityMock.NOW.minusHours(2));
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(ended));
        assertThatThrownBy(() -> service.register(1L, CommunityMock.local())).isInstanceOf(ConflictException.class).hasMessageContaining("ended");

        when(actions.findForUpdate(2L)).thenReturn(Optional.of(CommunityMock.action(ActionStatus.CANCELLED)));
        assertThatThrownBy(() -> service.register(2L, CommunityMock.local())).isInstanceOf(ConflictException.class);

        when(actions.findForUpdate(3L)).thenReturn(Optional.of(CommunityMock.action(ActionStatus.DRAFT)));
        assertThatThrownBy(() -> service.register(3L, CommunityMock.local())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_registered_freesSpot_cancelledIsIdempotent_confirmedIs409() {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        CommunityRegistration r = CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.REGISTERED);
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.of(r));

        service.cancel(1L, CommunityMock.local());
        assertThat(r.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(a.getRegisteredCount()).isEqualTo(17);

        service.cancel(1L, CommunityMock.local());           // a doua oară: nimic
        assertThat(a.getRegisteredCount()).isEqualTo(17);

        r.setStatus(RegistrationStatus.CONFIRMED);
        assertThatThrownBy(() -> service.cancel(1L, CommunityMock.local())).isInstanceOf(ConflictException.class);
    }

    @Test
    void cancel_notRegistered_is404() {
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(CommunityMock.action(1L, 40, 18)));
        when(registrations.findByActionIdAndUserId(1L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(1L, CommunityMock.local())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_draft_isHiddenFromPublic_butVisibleToCityHall() {
        when(actions.findById(4L)).thenReturn(Optional.of(CommunityMock.action(ActionStatus.DRAFT)));
        assertThatThrownBy(() -> service.get(4L, Optional.of(CommunityMock.local()))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.get(4L, Optional.empty())).isInstanceOf(NotFoundException.class);

        when(registrations.going(List.of(1L))).thenReturn(List.of());
        when(registrations.findByUserIdAndActionIdInAndStatusNot(4L, List.of(1L), RegistrationStatus.CANCELLED)).thenReturn(List.of());
        assertThat(service.get(4L, Optional.of(CommunityMock.cityHall())).action().getStatus()).isEqualTo(ActionStatus.DRAFT);
    }

    @Test
    void views_goingPreview_isFirstNamesOfLastThree() {
        CommunityAction a = CommunityMock.action(1L, 40, 4);
        when(registrations.going(List.of(1L))).thenReturn(List.of(going(1L, "Radu M."), going(1L, "Elena T."), going(1L, "Mihai R."), going(1L, "Ioana S.")));

        CommunityService.ActionView v = service.views(List.of(a), null).get(0);

        assertThat(v.goingPreview()).containsExactly("Radu", "Elena", "Mihai");
        assertThat(v.myRegistration()).isNull();
    }

    private static CommunityRegistrationRepository.GoingRow going(long actionId, String name) {
        return new CommunityRegistrationRepository.GoingRow() {
            public Long getActionId() { return actionId; }
            public String getDisplayName() { return name; }
        };
    }
}
