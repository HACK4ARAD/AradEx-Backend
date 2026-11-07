package aradexperience.service.points;

import aradexperience.entity.points.PointsTransaction;
import aradexperience.entity.users.User;
import aradexperience.enums.PointsType;
import aradexperience.enums.Role;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.CommunityMock;
import aradexperience.mock.UserMock;
import aradexperience.repository.community.CommunityRegistrationRepository;
import aradexperience.repository.points.PointsTransactionRepository;
import aradexperience.repository.users.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsServiceTest {

    @Mock PointsTransactionRepository transactions;
    @Mock CommunityRegistrationRepository registrations;
    @Mock UserRepository users;
    @Mock EntityManager em;
    @InjectMocks PointsService service;

    User ana;

    @BeforeEach
    void setUp() {
        service.clock = CommunityMock.CLOCK;
        ana = UserMock.local();                      // 270 de puncte
        lenient().when(em.find(User.class, 2L)).thenReturn(ana);
        lenient().when(transactions.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void award_locksUser_addsToBalance_andWritesPositiveTransaction() {
        PointsTransaction t = service.award(2L, 50, PointsType.EARN_ACTION, PointsTransaction.REF_ACTION, 1L, "Plantare");

        verify(em).flush();
        verify(em).refresh(ana, LockModeType.PESSIMISTIC_WRITE);
        assertThat(ana.getPointsBalance()).isEqualTo(320);    // entitatea din tranzacția apelantului e la zi
        assertThat(t.getAmount()).isEqualTo(50);
        assertThat(t.getType()).isEqualTo(PointsType.EARN_ACTION);
        assertThat(t.getRefId()).isEqualTo(1L);
        assertThat(t.getCreatedAt().toInstant()).isEqualTo(CommunityMock.NOW.toInstant());
    }

    @Test
    void award_unknownUser_is404_andWritesNothing() {
        assertThatThrownBy(() -> service.award(99L, 50, PointsType.EARN_ACTION, "ACTION", 1L, null)).isInstanceOf(NotFoundException.class);
        verify(transactions, never()).save(any());
    }

    @Test
    void spend_insufficientBalance_is409_andWritesNothing() {
        assertThatThrownBy(() -> service.spend(2L, 300, PointsTransaction.REF_OFFER, 1L, "Cafea"))
                .isInstanceOf(ConflictException.class).hasMessage(PointsService.INSUFFICIENT_POINTS);
        assertThat(ana.getPointsBalance()).isEqualTo(270);
        verify(transactions, never()).save(any());
    }

    @Test
    void spend_writesNegativeSpendTransaction() {
        PointsTransaction t = service.spend(2L, 30, PointsTransaction.REF_OFFER, 1L, "Cafea -20%");

        assertThat(t.getAmount()).isEqualTo(-30);
        assertThat(t.getType()).isEqualTo(PointsType.SPEND_OFFER);
        assertThat(ana.getPointsBalance()).isEqualTo(240);
    }

    @Test
    void adjust_negativeBelowZero_is409_positiveIsAdjust() {
        assertThatThrownBy(() -> service.adjust(2L, -500, "corecție")).isInstanceOf(ConflictException.class);

        ArgumentCaptor<PointsTransaction> saved = ArgumentCaptor.forClass(PointsTransaction.class);
        service.adjust(2L, 20, "bonus");
        verify(transactions).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(PointsType.ADJUST);
        assertThat(saved.getValue().getRefType()).isEqualTo(PointsTransaction.REF_ADMIN);
        assertThat(ana.getPointsBalance()).isEqualTo(290);
    }

    @Test
    void standing_isMyPlaceInTheMonth_orFirstPlaceAfterEveryone() {
        when(transactions.board(any(), any())).thenReturn(List.of(row(6L, 110), row(2L, 50)));
        assertThat(service.standing(PointsService.Period.MONTH, 2L)).isEqualTo(new aradexperience.dto.community.LeaderboardDto.Me(2, 50));
        assertThat(service.standing(PointsService.Period.MONTH, 77L)).isEqualTo(new aradexperience.dto.community.LeaderboardDto.Me(3, 0));
    }

    @Test
    void board_ranksByPoints_namesTop_andPlacesMeAfterEveryoneWhenWithoutPoints() {
        when(transactions.board(any(), any())).thenReturn(List.of(row(6L, 110), row(2L, 50)));
        User mihai = UserMock.user(6L, "mihai.r@exemplu.ro", Role.LOCAL);
        mihai.setDisplayName("Mihai R.");
        when(users.findAllById(List.of(6L, 2L))).thenReturn(List.of(mihai, UserMock.local()));
        when(registrations.participation(any(), any())).thenReturn(List.of());
        when(registrations.lastActions(any(), any())).thenReturn(List.of());

        PointsService.Board withMe = service.board(PointsService.Period.MONTH, 2L);
        assertThat(withMe.entries()).extracting(e -> e.rank() + ":" + e.displayName() + ":" + e.points())
                .containsExactly("1:Mihai R.:110", "2:Ana Popescu:50");
        assertThat(withMe.me().rank()).isEqualTo(2);

        PointsService.Board newcomer = service.board(PointsService.Period.MONTH, 77L);
        assertThat(newcomer.me().rank()).isEqualTo(3);
        assertThat(newcomer.me().points()).isZero();
        assertThat(service.board(PointsService.Period.YEAR, null).me()).isNull();
    }

    @Test
    void range_isCurrentMonthOrYear_inAradTime() {
        OffsetDateTime[] month = service.range(PointsService.Period.MONTH);
        assertThat(month[0]).isEqualTo(OffsetDateTime.parse("2026-11-01T00:00:00+02:00"));
        assertThat(month[1]).isEqualTo(OffsetDateTime.parse("2026-12-01T00:00:00+02:00"));
        assertThat(service.range(PointsService.Period.YEAR)[0]).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00+02:00"));
    }

    @Test
    void streakMonths_countsConsecutiveMonthsBackFromNow() {
        when(transactions.earnedAt(2L)).thenReturn(List.of(
                OffsetDateTime.parse("2026-11-01T11:00:00+02:00"), OffsetDateTime.parse("2026-10-10T12:00:00+03:00"),
                OffsetDateTime.parse("2026-03-21T11:00:00+02:00")));
        assertThat(service.streakMonths(2L)).isEqualTo(2);

        // luna asta încă nimic → seria se numără de la luna trecută
        when(transactions.earnedAt(6L)).thenReturn(List.of(OffsetDateTime.parse("2026-10-25T10:00:00+03:00"), OffsetDateTime.parse("2026-09-12T10:00:00+03:00")));
        assertThat(service.streakMonths(6L)).isEqualTo(2);
        when(transactions.earnedAt(7L)).thenReturn(List.of());
        assertThat(service.streakMonths(7L)).isZero();
    }

    @Test
    void period_parse() {
        assertThat(PointsService.Period.parse(null)).isEqualTo(PointsService.Period.MONTH);
        assertThat(PointsService.Period.parse("YEAR")).isEqualTo(PointsService.Period.YEAR);
        assertThatThrownBy(() -> PointsService.Period.parse("week")).isInstanceOf(BadRequestException.class);
    }

    private static PointsTransactionRepository.BoardRow row(long userId, long points) {
        return new PointsTransactionRepository.BoardRow() {
            public Long getUserId() { return userId; }
            public Long getPoints() { return points; }
        };
    }
}
