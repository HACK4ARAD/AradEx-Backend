package aradexperience.mock;

import aradexperience.dto.community.ActionCreate;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.community.CommunityRegistration;
import aradexperience.enums.ActionCategory;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.RegistrationStatus;
import aradexperience.enums.Role;
import aradexperience.security.CurrentUser;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Date de test pentru CommunityService / CityCommunityService / PointsService și controllerele lor (T11). */
public final class CommunityMock {

    /** „Acum” în teste: sâmbătă, 7 noiembrie 2026, 09:00 la Arad. */
    public static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-07T09:00:00+02:00");
    public static final Clock CLOCK = Clock.fixed(Instant.from(NOW), ZoneId.of("Europe/Bucharest"));

    private CommunityMock() {}

    public static CommunityAction action(long id, int capacity, int registered) {
        CommunityAction a = new CommunityAction();
        a.setId(id);
        a.setTitle("Plantăm copaci pe Faleza Mureșului");
        a.setDescription("Plantăm 120 de tei și stejari.");
        a.setCategory(ActionCategory.ENVIRONMENT);
        a.setOrganizer("Direcția Spații Verzi");
        a.setStartAt(NOW.plusHours(1));
        a.setEndAt(NOW.plusHours(4));
        a.setLocationName("Faleza Mureșului");
        a.setLat(46.1745);
        a.setLng(21.3215);
        a.setPointsReward(50);
        a.setCapacity(capacity);
        a.setRegisteredCount(registered);
        a.setStatus(ActionStatus.PUBLISHED);
        a.setCreatedBy(4L);
        return a;
    }

    public static CommunityAction action(ActionStatus status) {
        CommunityAction a = action(1L, 40, 18);
        a.setStatus(status);
        return a;
    }

    public static CommunityRegistration registration(long id, long actionId, long userId, RegistrationStatus status) {
        CommunityRegistration r = new CommunityRegistration();
        r.setId(id);
        r.setActionId(actionId);
        r.setUserId(userId);
        r.setCode("AX-C" + actionId + "-K4N7PA");
        r.setStatus(status);
        r.setRegisteredAt(NOW.minusDays(1));
        return r;
    }

    public static CurrentUser local() {
        return new CurrentUser(2L, "local@demo.ro", Role.LOCAL);
    }

    public static CurrentUser cityHall() {
        return new CurrentUser(4L, "primaria@demo.ro", Role.CITY_HALL);
    }

    public static ActionCreate create(OffsetDateTime start, OffsetDateTime end, int capacity) {
        return new ActionCreate("Plantăm flori în Parcul Copiilor", "Ce facem, ce aduc oamenii.", ActionCategory.ENVIRONMENT,
                "Direcția Spații Verzi", start, end, "Parcul Copiilor", null, null, null, 50, capacity, null, null, null);
    }
}
