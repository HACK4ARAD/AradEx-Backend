package aradexperience.mock;

import aradexperience.dto.business.AnnouncementCreate;
import aradexperience.dto.events.EventCreate;
import aradexperience.entity.business.Announcement;
import aradexperience.entity.business.Business;
import aradexperience.entity.events.Event;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.Role;
import aradexperience.enums.TicketType;
import aradexperience.security.CurrentUser;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Date de test pentru BusinessService / BusinessController (T09): două firme, evenimente și anunțuri. */
public final class BusinessMock {

    public static final long OWNER_ID = 3L;          // firma@demo.ro → Cafeneaua din Pasaj (id 1)
    public static final long OTHER_OWNER_ID = 14L;   // Crama Dealul Miniș (id 3)
    public static final OffsetDateTime START = OffsetDateTime.parse("2030-11-29T16:00:00+02:00");

    private BusinessMock() {}

    public static Business pasaj() {
        Business b = new Business();
        b.setId(1L);
        b.setOwnerUserId(OWNER_ID);
        b.setName("Cafeneaua din Pasaj");
        b.setCategory(BusinessCategory.CAFE);
        b.setAddress("Str. Vasile Alecsandri 1, Arad");
        b.setLat(46.1730);
        b.setLng(21.3148);
        b.setVerified(true);
        b.setViewsCount(1240);
        return b;
    }

    public static Business unverified() {
        Business b = pasaj();
        b.setVerified(false);
        return b;
    }

    public static CurrentUser owner() {
        return new CurrentUser(OWNER_ID, "firma@demo.ro", Role.BUSINESS);
    }

    public static CurrentUser otherOwner() {
        return new CurrentUser(OTHER_OWNER_ID, "crama@demo.ro", Role.BUSINESS);
    }

    public static CurrentUser admin() {
        return new CurrentUser(5L, "admin@demo.ro", Role.ADMIN);
    }

    public static Business crama() {
        Business b = pasaj();
        b.setId(3L);
        b.setOwnerUserId(OTHER_OWNER_ID);
        b.setName("Crama Dealul Miniș");
        return b;
    }

    /** Un eveniment al firmei `businessId`. */
    public static Event event(long id, long businessId) {
        Event e = new Event();
        e.setId(id);
        e.setTitle("Seară de jazz la Pasaj");
        e.setDescription("Jazz live, intrare liberă.");
        e.setCategory(EventCategory.CONCERT);
        e.setOrganizerType(OrganizerType.BUSINESS);
        e.setOrganizerBusinessId(businessId);
        e.setOrganizerName("Cafeneaua din Pasaj");
        e.setLocationName("Cafeneaua din Pasaj");
        e.setAddress("Str. Vasile Alecsandri 1, Arad");
        e.setLat(46.1730);
        e.setLng(21.3148);
        e.setStartAt(START);
        e.setTicketType(TicketType.FREE);
        e.setPriceFrom(BigDecimal.ZERO);
        e.setCapacity(80);
        e.setParticipantsCount(62);
        e.setSavesCount(24);
        e.setStatus(ContentStatus.PUBLISHED);
        return e;
    }

    public static EventCreate eventCreate(TicketType type, BigDecimal priceAdult, OffsetDateTime endAt) {
        return new EventCreate("Atelier latte art", "Învățăm să desenăm în spuma laptelui.", EventCategory.FOOD, null, null, null,
                null, null, START, endAt, type, priceAdult, null, null, 10, null);
    }

    public static EventCreate inAppEvent() {
        return eventCreate(TicketType.IN_APP, new BigDecimal("40"), START.plusMinutes(90));
    }

    public static Announcement announcement(long id, long businessId) {
        Announcement a = new Announcement();
        a.setId(id);
        a.setBusinessId(businessId);
        a.setTitle("Program prelungit de Black Friday");
        a.setBody("Vineri și sâmbătă suntem deschiși până la 23:00.");
        a.setStatus(ContentStatus.PUBLISHED);
        a.setCreatedAt(OffsetDateTime.parse("2026-11-05T10:00:00+02:00"));
        return a;
    }

    public static AnnouncementCreate announcementCreate() {
        return new AnnouncementCreate("Căutăm barista part-time", "Trimite CV-ul la cafenea.", null);
    }
}
