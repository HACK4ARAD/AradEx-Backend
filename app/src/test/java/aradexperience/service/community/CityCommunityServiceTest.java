package aradexperience.service.community;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.entity.points.PointsTransaction;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.PointsType;
import aradexperience.enums.RegistrationStatus;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.CommunityMock;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.community.CommunityStatsRepository;
import aradexperience.repository.points.PointsTransactionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.service.points.PointsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CityCommunityServiceTest {

    @Mock CommunityActionRepository actions;
    @Mock CommunityRegistrationRepository registrations;
    @Mock CommunityStatsRepository stats;
    @Mock PointsTransactionRepository transactions;
    @Mock PointsService points;
    @Mock UserRepository users;

    CommunityService community;
    CityCommunityService service;

    static final String CODE = "AX-C1-K4N7PA";

    @BeforeEach
    void setUp() {
        community = new CommunityService(actions, registrations, users);
        community.clock = CommunityMock.CLOCK;
        service = new CityCommunityService(actions, registrations, stats, transactions, points, community);
        lenient().when(users.findAllById(any())).thenReturn(java.util.List.of());
    }

    private CommunityRegistration stubCode(RegistrationStatus status, CommunityAction a) {
        CommunityRegistration r = CommunityMock.registration(5L, 1L, 2L, status);
        when(registrations.actionIdByCode(CODE)).thenReturn(Optional.of(1L));
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        when(registrations.findForUpdateByCode(CODE)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void confirmByCode_awardsPointsOnce_throughPointsService() {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        CommunityRegistration r = stubCode(RegistrationStatus.REGISTERED, a);

        CityCommunityService.ConfirmView first = service.confirmByCode(" ax-c1-k4n7pa ", CommunityMock.cityHall());

        assertThat(first.alreadyConfirmed()).isFalse();
        assertThat(first.pointsAwarded()).isEqualTo(50);
        assertThat(r.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(r.getConfirmedBy()).isEqualTo(4L);
        assertThat(a.getConfirmedCount()).isEqualTo(1);
        verify(points).award(2L, 50, PointsType.EARN_ACTION, PointsTransaction.REF_ACTION, 1L, a.getTitle());

        // a doua scanare a aceluiași QR: 200, fără puncte în plus
        CityCommunityService.ConfirmView second = service.confirmByCode(CODE, CommunityMock.cityHall());
        assertThat(second.alreadyConfirmed()).isTrue();
        assertThat(second.pointsAwarded()).isZero();
        assertThat(a.getConfirmedCount()).isEqualTo(1);
        verify(points, times(1)).award(anyLong(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void confirmByCode_unknown_is404() {
        when(registrations.actionIdByCode("AX-C1-ZZZZZZ")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmByCode("AX-C1-ZZZZZZ", CommunityMock.cityHall())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void confirm_cancelledRegistration_is409_andAwardsNothing() {
        stubCode(RegistrationStatus.CANCELLED, CommunityMock.action(1L, 40, 18));
        assertThatThrownBy(() -> service.confirmByCode(CODE, CommunityMock.cityHall())).isInstanceOf(ConflictException.class);
        verify(points, never()).award(anyLong(), anyInt(), any(), any(), any(), any());
    }

    @Test
    void confirm_draftAction_is409() {
        stubCode(RegistrationStatus.REGISTERED, CommunityMock.action(ActionStatus.DRAFT));
        assertThatThrownBy(() -> service.confirmByCode(CODE, CommunityMock.cityHall())).isInstanceOf(ConflictException.class);
    }

    @Test
    void confirm_afterReject_takesSpotBackAndAwards() {
        CommunityAction a = CommunityMock.action(1L, 40, 17);
        when(registrations.actionIdById(5L)).thenReturn(Optional.of(1L));
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        when(registrations.findForUpdate(5L)).thenReturn(Optional.of(CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.NO_SHOW)));

        assertThat(service.confirmById(5L, CommunityMock.cityHall()).pointsAwarded()).isEqualTo(50);
        assertThat(a.getRegisteredCount()).isEqualTo(18);
    }

    @Test
    void reject_registered_isNoShow_andFreesSpot_confirmedIs409() {
        CommunityAction a = CommunityMock.action(1L, 40, 18);
        CommunityRegistration r = CommunityMock.registration(5L, 1L, 2L, RegistrationStatus.REGISTERED);
        when(registrations.actionIdById(5L)).thenReturn(Optional.of(1L));
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(a));
        when(registrations.findForUpdate(5L)).thenReturn(Optional.of(r));

        assertThat(service.reject(5L, CommunityMock.cityHall()).registration().getStatus()).isEqualTo(RegistrationStatus.NO_SHOW);
        assertThat(a.getRegisteredCount()).isEqualTo(17);

        r.setStatus(RegistrationStatus.CONFIRMED);
        assertThatThrownBy(() -> service.reject(5L, CommunityMock.cityHall())).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_endBeforeStart_is400_andDoneStatusIs400() {
        assertThatThrownBy(() -> service.create(CommunityMock.create(CommunityMock.NOW, CommunityMock.NOW, 20), CommunityMock.cityHall()))
                .isInstanceOf(BadRequestException.class);
        var done = new aradexperience.dto.community.ActionCreate("T", "D", aradexperience.enums.ActionCategory.SOCIAL, null,
                CommunityMock.NOW, CommunityMock.NOW.plusHours(1), "Loc", null, null, null, 50, 20, null, null, ActionStatus.DONE);
        assertThatThrownBy(() -> service.create(done, CommunityMock.cityHall())).isInstanceOf(BadRequestException.class);
        verify(actions, never()).save(any());
    }

    @Test
    void create_defaultsToPublished_andAradCenter() {
        when(actions.save(any())).thenAnswer(i -> i.getArgument(0));
        CommunityAction a = service.create(CommunityMock.create(CommunityMock.NOW, CommunityMock.NOW.plusHours(3), 20), CommunityMock.cityHall()).action();
        assertThat(a.getStatus()).isEqualTo(ActionStatus.PUBLISHED);
        assertThat(a.getCreatedBy()).isEqualTo(4L);
        assertThat(a.getLat()).isEqualTo(aradexperience.util.Geo.ARAD_LAT);
    }

    @Test
    void update_capacityBelowRegistered_is409() {
        when(actions.findForUpdate(1L)).thenReturn(Optional.of(CommunityMock.action(1L, 40, 18)));
        assertThatThrownBy(() -> service.update(1L, CommunityMock.create(CommunityMock.NOW, CommunityMock.NOW.plusHours(3), 10)))
                .isInstanceOf(ConflictException.class);
    }
}
