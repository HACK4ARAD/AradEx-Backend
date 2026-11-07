package aradexperience.mock;

import aradexperience.entity.events.Event;
import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.EventCategory;
import aradexperience.enums.TicketType;

import java.math.BigDecimal;

/** Date de test pentru bilete: teatrul de păpuși (40 / 25 lei, 120 de locuri) și un bilet emis. */
public final class TicketMock {

    private TicketMock() {}

    public static Event puppets(int participants) {
        Event e = EventMock.event(22, "Teatru de păpuși: Croitorașul cel viteaz", EventCategory.FAMILY, "2026-11-07T16:00:00+02:00", TicketType.IN_APP);
        e.setEndAt(e.getStartAt().plusMinutes(50));
        e.setParticipantsCount(participants);
        e.setCreatedBy(5L);
        return e;
    }

    public static Event freeWalk(Integer capacity) {
        Event e = EventMock.event(23, "Plimbare ghidată: Cetatea văzută de pe Faleză", EventCategory.OUTDOOR, "2026-11-07T17:00:00+02:00", TicketType.FREE);
        e.setCapacity(capacity);
        return e;
    }

    public static Ticket ticket(String code, Event e, long userId) {
        Ticket t = new Ticket();
        t.setId(10L);
        t.setCode(code);
        t.setEventId(e.getId());
        t.setUserId(userId);
        t.setQtyAdult(1);
        t.setQtyChild(1);
        t.setTotal(new BigDecimal("65"));
        t.setEmail("ana@exemplu.ro");
        return t;
    }
}
