package aradexperience.repository.community;

import aradexperience.entity.community.CommunityAction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

/** Numerele din panoul Primăriei (W2). Doar citire; evenimentele (T06/T09) și voluntarii (T18) sunt doar numărați aici. */
public interface CommunityStatsRepository extends Repository<CommunityAction, Long> {

    @Query("""
            select count(e) from aradexperience.entity.events.Event e
            where e.status = aradexperience.enums.ContentStatus.PUBLISHED and e.startAt >= :now""")
    long upcomingEvents(@Param("now") OffsetDateTime now);

    /** Înscriși (REGISTERED + CONFIRMED) la acțiunile publicate care nu s-au încheiat. */
    @Query("""
            select coalesce(sum(a.registeredCount), 0) from CommunityAction a
            where a.status = aradexperience.enums.ActionStatus.PUBLISHED and a.endAt > :now""")
    long upcomingRegistrations(@Param("now") OffsetDateTime now);

    /** Înscrieri încă neconfirmate la acțiunile publicate (inclusiv cele de azi, deja începute). */
    @Query("""
            select count(r) from CommunityRegistration r, CommunityAction a
            where a.id = r.actionId and a.status = aradexperience.enums.ActionStatus.PUBLISHED
              and r.status = aradexperience.enums.RegistrationStatus.REGISTERED""")
    long pendingValidations();

    @Query("select count(u) from aradexperience.entity.users.User u where u.role = aradexperience.enums.Role.VOLUNTEER")
    long volunteers();
}
