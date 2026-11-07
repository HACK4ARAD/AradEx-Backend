package aradexperience.service.tickets;

import aradexperience.dto.tickets.TicketRequest;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.Role;
import aradexperience.enums.TicketStatus;
import aradexperience.enums.TicketType;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ConflictException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.mock.EventMock;
import aradexperience.mock.TicketMock;
import aradexperience.repository.business.BusinessRepository;
import aradexperience.repository.events.EventRepository;
import aradexperience.repository.tickets.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T07 · Verificare: capacitate (409), total, cod unic, înscriere gratuită, folosire la intrare, .ics. */
class TicketServiceTest {

    EventRepository events = mock(EventRepository.class);
    TicketRepository tickets = mock(TicketRepository.class);
    BusinessRepository businesses = mock(BusinessRepository.class);
    TicketService service = new TicketService(events, tickets, businesses);

    @BeforeEach
    void setUp() {
        service.setClock(Clock.fixed(EventMock.NOW.toInstant(), aradexperience.service.events.EventService.ZONE));
        when(tickets.saveAndFlush(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TicketRequest req(int adult, int child) {
        return new TicketRequest(adult, child, " Ana@Exemplu.ro ");
    }

    @Test
    void buy_computesTotal_codeAndParticipants() {
        Event e = TicketMock.puppets(86);
        when(events.findForUpdate(22L)).thenReturn(Optional.of(e));

        var v = service.buy(22L, req(2, 1), EventMock.user(7, Role.LOCAL));

        assertThat(v.ticket().getTotal()).isEqualByComparingTo("105");
        assertThat(v.ticket().getCode()).matches("AX-T22-[A-Z2-9]{6}");
        assertThat(v.ticket().getEmail()).isEqualTo("ana@exemplu.ro");
        assertThat(e.getParticipantsCount()).isEqualTo(89);
    }

    @Test
    void buy_overCapacity_is409_andNothingSaved() {
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(119)));
        assertThatThrownBy(() -> service.buy(22L, req(2, 0), EventMock.user(7, Role.LOCAL)))
                .isInstanceOf(ConflictException.class).hasMessage("Only 1 spots left");
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void buy_soldOut_is409() {
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(120)));
        assertThatThrownBy(() -> service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(ConflictException.class).hasMessage("Sold out");
    }

    @Test
    void buy_retriesOnDuplicateCode() {
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(0)));
        when(tickets.saveAndFlush(any(Ticket.class))).thenThrow(new DataIntegrityViolationException("dup")).thenAnswer(inv -> inv.getArgument(0));
        service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL));
        verify(tickets, times(2)).saveAndFlush(any());
    }

    @Test
    void buy_externalEvent_is400() {
        Event e = TicketMock.puppets(0);
        e.setTicketType(TicketType.EXTERNAL);
        when(events.findForUpdate(22L)).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void buy_zeroTickets_endedEvent_draft_areRejected() {
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(0)));
        assertThatThrownBy(() -> service.buy(22L, req(0, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(BadRequestException.class);

        Event past = TicketMock.puppets(0);
        past.setStartAt(EventMock.NOW.minusHours(3));
        past.setEndAt(EventMock.NOW.minusHours(2));
        when(events.findForUpdate(22L)).thenReturn(Optional.of(past));
        assertThatThrownBy(() -> service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(BadRequestException.class);

        Event draft = TicketMock.puppets(0);
        draft.setStatus(ContentStatus.DRAFT);
        when(events.findForUpdate(22L)).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void freeRegistration_onePerUser_free() {
        Event walk = TicketMock.freeWalk(60);
        when(events.findForUpdate(23L)).thenReturn(Optional.of(walk));
        var v = service.buy(23L, req(1, 0), EventMock.user(7, Role.LOCAL));
        assertThat(v.ticket().getTotal()).isZero();

        when(tickets.existsByUserIdAndEventIdAndStatusNot(7L, 23L, TicketStatus.CANCELLED)).thenReturn(true);
        assertThatThrownBy(() -> service.buy(23L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.buy(23L, req(2, 0), EventMock.user(8, Role.LOCAL))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void freeWithoutCapacity_needsNoRegistration() {
        when(events.findForUpdate(23L)).thenReturn(Optional.of(TicketMock.freeWalk(null)));
        assertThatThrownBy(() -> service.buy(23L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void mine_splitsUpcomingAndPast() {
        Event today = TicketMock.puppets(0);
        Event past = TicketMock.freeWalk(60);
        past.setId(5L);
        past.setStartAt(EventMock.NOW.minusDays(20));
        when(tickets.findByUserIdAndStatusNot(7L, TicketStatus.CANCELLED)).thenReturn(List.of(TicketMock.ticket("AX-T5-AAAAAA", past, 7), TicketMock.ticket("AX-T22-BBBBBB", today, 7)));
        when(events.findAllById(any())).thenReturn(List.of(today, past));

        assertThat(service.mine(EventMock.user(7, Role.LOCAL), true)).extracting(v -> v.ticket().getCode()).containsExactly("AX-T22-BBBBBB");
        assertThat(service.mine(EventMock.user(7, Role.LOCAL), false)).extracting(v -> v.ticket().getCode()).containsExactly("AX-T5-AAAAAA");
        assertThat(service.mine(EventMock.user(7, Role.LOCAL), null)).hasSize(2);
    }

    @Test
    void get_ownerOrganizerAdmin_others403() {
        Event e = TicketMock.puppets(1);
        when(tickets.findByCode("AX-T22-BBBBBB")).thenReturn(Optional.of(TicketMock.ticket("AX-T22-BBBBBB", e, 7)));
        when(events.findById(22L)).thenReturn(Optional.of(e));
        assertThat(service.get("ax-t22-bbbbbb", EventMock.user(7, Role.LOCAL))).isNotNull();
        assertThat(service.get("AX-T22-BBBBBB", EventMock.user(5, Role.CITY_HALL))).isNotNull();
        assertThat(service.get("AX-T22-BBBBBB", EventMock.user(1, Role.ADMIN))).isNotNull();
        assertThatThrownBy(() -> service.get("AX-T22-BBBBBB", EventMock.user(8, Role.LOCAL))).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void use_byBusinessOwner_thenSecondTime409() {
        Event e = TicketMock.puppets(1);
        e.setCreatedBy(null);
        e.setOrganizerBusinessId(3L);
        Ticket t = TicketMock.ticket("AX-T22-BBBBBB", e, 7);
        Business b = new Business();
        b.setId(3L);
        when(tickets.findByCode("AX-T22-BBBBBB")).thenReturn(Optional.of(t));
        when(events.findById(22L)).thenReturn(Optional.of(e));
        when(businesses.findByOwnerUserId(4L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.use("AX-T22-BBBBBB", EventMock.user(7, Role.LOCAL))).isInstanceOf(ForbiddenException.class);
        service.use("AX-T22-BBBBBB", EventMock.user(4, Role.BUSINESS));
        assertThat(t.getStatus()).isEqualTo(TicketStatus.USED);
        assertThat(t.getUsedAt().toInstant()).isEqualTo(EventMock.NOW.toInstant());
        assertThatThrownBy(() -> service.use("AX-T22-BBBBBB", EventMock.user(4, Role.BUSINESS))).isInstanceOf(ConflictException.class);
    }

    @Test
    void ics_hasUtcTimes_andEscapedText() {
        Event e = TicketMock.puppets(0);
        e.setDescription("Copii, părinți; bunici");
        when(events.findById(22L)).thenReturn(Optional.of(e));
        String ics = service.ics(22L);
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").contains("DTSTART:20261107T140000Z", "DTEND:20261107T145000Z")
                .contains("DESCRIPTION:Copii\\, părinți\\; bunici").endsWith("END:VCALENDAR\r\n");
    }

    // --- T25: plata cu cardul (Stripe, mod test) ---------------------------------------------------------------------

    @Test
    void buy_paidTickets_whenCardPaymentIsOn_is402_freeRegistrationStillWorks() {
        service.setPayments(() -> true);
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(86)));
        assertThatThrownBy(() -> service.buy(22L, req(1, 0), EventMock.user(7, Role.LOCAL)))
                .isInstanceOfSatisfying(aradexperience.exceptions.ApiException.class, e -> assertThat(e.status().value()).isEqualTo(402));
        verify(tickets, never()).saveAndFlush(any());

        when(events.findForUpdate(30L)).thenReturn(Optional.of(TicketMock.freeWalk(20)));
        assertThat(service.buy(30L, new TicketRequest(1, 0, null), EventMock.user(7, Role.LOCAL)).ticket().getTotal()).isEqualByComparingTo("0");
    }

    @Test
    void quote_checksTheOrder_withoutSaving() {
        when(events.findById(22L)).thenReturn(Optional.of(TicketMock.puppets(86)));
        assertThat(service.quote(22L, req(2, 1), EventMock.user(7, Role.LOCAL)).total()).isEqualByComparingTo("105");
        when(events.findById(23L)).thenReturn(Optional.of(TicketMock.puppets(120)));
        assertThatThrownBy(() -> service.quote(23L, req(1, 0), EventMock.user(7, Role.LOCAL))).isInstanceOf(ConflictException.class);
        verify(tickets, never()).saveAndFlush(any());
    }

    @Test
    void issuePaid_savesThePaidAmountAndIntent_andIsIdempotent() {
        Event e = TicketMock.puppets(86);
        when(events.findForUpdate(22L)).thenReturn(Optional.of(e));
        when(tickets.findByPaymentIntentId("pi_1")).thenReturn(Optional.empty());
        var v = service.issuePaid(22L, req(2, 1), 7L, "pi_1", new java.math.BigDecimal("105.00"));
        assertThat(v.ticket().getPaymentIntentId()).isEqualTo("pi_1");
        assertThat(v.ticket().getTotal()).isEqualByComparingTo("105");
        assertThat(e.getParticipantsCount()).isEqualTo(89);

        when(tickets.findByPaymentIntentId("pi_1")).thenReturn(Optional.of(v.ticket()));
        assertThat(service.issuePaid(22L, req(2, 1), 7L, "pi_1", new java.math.BigDecimal("105.00")).ticket()).isSameAs(v.ticket());
        assertThat(e.getParticipantsCount()).isEqualTo(89);
        verify(tickets, times(1)).saveAndFlush(any());
    }

    @Test
    void issuePaid_soldOutMeanwhile_is409() {
        when(events.findForUpdate(22L)).thenReturn(Optional.of(TicketMock.puppets(120)));
        when(tickets.findByPaymentIntentId("pi_2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.issuePaid(22L, req(1, 0), 7L, "pi_2", java.math.BigDecimal.TEN)).isInstanceOf(ConflictException.class);
    }
}
