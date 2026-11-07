package aradexperience.service.volunteer;

import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.VolunteerActionState;
import aradexperience.service.points.PointsService;
import lombok.experimental.UtilityClass;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * Regulile T18 (docs/PLAN.md): o acțiune alocată se deschide pentru scanare cu 30 de minute înainte de start și rămâne deschisă
 * până la sfârșitul zilei în care se termină (ora Aradului: întârziații se pot confirma și după ora de final); un cont de unică
 * folosință expiră după ultima acțiune alocată.
 */
@UtilityClass
public class VolunteerRules {

    public static final Duration OPENS_BEFORE = Duration.ofMinutes(30);

    public static OffsetDateTime opensAt(CommunityAction a) {
        return a.getStartAt().minus(OPENS_BEFORE);
    }

    /** Miezul nopții de după ziua în care se termină acțiunea, ora Aradului. */
    public static OffsetDateTime closesAt(CommunityAction a) {
        return a.getEndAt().atZoneSameInstant(PointsService.ZONE).toLocalDate().plusDays(1).atStartOfDay(PointsService.ZONE).toOffsetDateTime();
    }

    public static VolunteerActionState state(CommunityAction a, OffsetDateTime now) {
        if (a.getStatus() == ActionStatus.DONE || !now.isBefore(closesAt(a))) return VolunteerActionState.ENDED;
        return now.isBefore(opensAt(a)) ? VolunteerActionState.LOCKED : VolunteerActionState.OPEN;
    }

    /** Doar acțiunile publicate (sau încheiate) contează pentru voluntar; ciornele și cele anulate nu apar. */
    public static boolean visible(CommunityAction a) {
        return a.getStatus() == ActionStatus.PUBLISHED || a.getStatus() == ActionStatus.DONE;
    }

    /** Unică folosință + are acțiuni + toate s-au încheiat. Fără acțiuni alocate nu expiră (Primăria îi poate aloca una nouă). */
    public static boolean expired(Volunteer v, Collection<CommunityAction> allocated, OffsetDateTime now) {
        return v.isOneTime() && !allocated.isEmpty() && allocated.stream().allMatch(a -> state(a, now) == VolunteerActionState.ENDED);
    }
}
