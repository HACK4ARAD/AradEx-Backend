package aradexperience.mock;

import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.repository.admin.AdminContentRepository;
import aradexperience.security.CurrentUser;

import java.time.OffsetDateTime;

/** Date de test pentru T14 (administrare, firme partenere). */
public final class AdminMock {

    public static final long ADMIN_ID = 5L;
    public static final long CITY_ID = 4L;
    public static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-11-07T15:48:01Z");

    private AdminMock() {}

    public static CurrentUser admin() {
        return new CurrentUser(ADMIN_ID, "admin@demo.ro", Role.ADMIN);
    }

    public static CurrentUser cityHall() {
        return new CurrentUser(CITY_ID, "primaria@demo.ro", Role.CITY_HALL);
    }

    /** Brutăria Veche: firma din seed înregistrată singură, încă neverificată (are o ofertă în ciornă). */
    public static Business bakery() {
        Business b = new Business();
        b.setId(2L);
        b.setOwnerUserId(12L);
        b.setName("Brutăria Veche");
        b.setCategory(BusinessCategory.PRODUCER);
        b.setAddress("Str. Mețianu 3, Arad");
        b.setVerified(false);
        b.setCreatedAt(CREATED);
        return b;
    }

    public static User bakeryOwner() {
        User u = UserMock.user(12L, "brutarie@exemplu.ro", Role.BUSINESS);
        u.setDisplayName("Brutăria Veche");
        return u;
    }

    public static User local(long id) {
        User u = UserMock.user(id, null, Role.LOCAL);
        u.setPhone("+40799000002");
        u.setDisplayName("Bogdan D.");
        u.setCreatedAt(CREATED);
        return u;
    }

    public static AdminContentRepository.OfferCounts counts(long businessId, long active, long drafts, long used) {
        return new AdminContentRepository.OfferCounts() {
            public Long getBusinessId() { return businessId; }
            public Long getActive() { return active; }
            public Long getDrafts() { return drafts; }
            public Long getUsed() { return used; }
        };
    }
}
