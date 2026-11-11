package aradexperience.mock;

import aradexperience.config.AppProps;
import aradexperience.entity.events.Event;
import aradexperience.enums.EventCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.Role;
import aradexperience.enums.TicketType;
import aradexperience.security.CurrentUser;
import aradexperience.service.events.EventView;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** Date de test pentru evenimente: „azi” e sâmbătă 7 nov. 2026, ora 10:00 în Arad (08:00 UTC). */
public final class EventMock {

    public static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-07T10:00:00+02:00");
    public static final AppProps PROPS = new AppProps(null, null, null, new AppProps.Events(50, 20), false);

    private EventMock() {}

    public static Event event(long id, String title, EventCategory cat, String startAt, TicketType ticket) {
        Event e = new Event();
        e.setId(id);
        e.setTitle(title);
        e.setDescription("Descriere");
        e.setCategory(cat);
        e.setOrganizerType(OrganizerType.EXTERNAL);
        e.setOrganizerName("Filarmonica Arad");
        e.setLocationName("Palatul Cultural");
        e.setAddress("Piața George Enescu 1");
        e.setLat(46.1757);
        e.setLng(21.3171);
        e.setStartAt(OffsetDateTime.parse(startAt));
        e.setTicketType(ticket);
        e.setStatus(ContentStatus.PUBLISHED);
        if (ticket == TicketType.IN_APP) {
            e.setPriceAdult(new BigDecimal("40"));
            e.setPriceChild(new BigDecimal("25"));
            e.setPriceFrom(new BigDecimal("25"));
            e.setCapacity(120);
        }
        return e;
    }

    /** Patru evenimente: azi concert (popular), azi teatru în aplicație, mâine târg la Cetate, peste 10 zile sport. */
    public static List<Event> agenda() {
        Event concert = event(1, "Repetiție deschisă: Filarmonica Arad", EventCategory.CONCERT, "2026-11-07T18:00:00+02:00", TicketType.FREE);
        concert.setParticipantsCount(140);
        Event puppets = event(2, "Teatru de păpuși: Croitorașul cel viteaz", EventCategory.FAMILY, "2026-11-07T11:00:00+02:00", TicketType.IN_APP);
        puppets.setOrganizerType(OrganizerType.CITY);
        puppets.setOrganizerName("Primăria Arad");
        Event fair = event(3, "Târgul producătorilor", EventCategory.FAIR, "2026-11-08T09:00:00+02:00", TicketType.FREE);
        fair.setLocationName("Cetatea Aradului");
        fair.setLat(46.1711);
        fair.setLng(21.3319);
        fair.setSavesCount(25);
        Event uta = event(4, "UTA Arad – Petrolul Ploiești", EventCategory.SPORT, "2026-11-17T17:30:00+02:00", TicketType.EXTERNAL);
        uta.setLocationName("Arena Francisc Neuman");
        return List.of(concert, puppets, fair, uta);
    }

    public static EventView view(Event e) {
        return new EventView(e, 0.4, false, false, null);
    }

    public static CurrentUser user(long id, Role role) {
        return new CurrentUser(id, role.name().toLowerCase() + "@demo.ro", role);
    }
}
