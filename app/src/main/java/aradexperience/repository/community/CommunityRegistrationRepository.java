package aradexperience.repository.community;

import aradexperience.entity.community.CommunityRegistration;
import aradexperience.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityRegistrationRepository extends JpaRepository<CommunityRegistration, Long> {

    Optional<CommunityRegistration> findByActionIdAndUserId(long actionId, long userId);

    /**
     * Acțiunea unei înscrieri, fără să încarce entitatea: confirmarea / respingerea blochează ÎNTÂI acțiunea și abia apoi
     * înscrierea (aceeași ordine ca înscrierea și anularea), ca două tranzacții să nu se blocheze reciproc.
     */
    @Query("select r.actionId from CommunityRegistration r where r.code = :code")
    Optional<Long> actionIdByCode(@Param("code") String code);

    @Query("select r.actionId from CommunityRegistration r where r.id = :id")
    Optional<Long> actionIdById(@Param("id") long id);

    /** Blocată ca o confirmare dublă simultană (două scanări ale aceluiași QR) să acorde punctele o singură dată. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommunityRegistration r where r.code = :code")
    Optional<CommunityRegistration> findForUpdateByCode(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CommunityRegistration r where r.id = :id")
    Optional<CommunityRegistration> findForUpdate(@Param("id") long id);

    List<CommunityRegistration> findByActionIdOrderByRegisteredAtAsc(long actionId);

    List<CommunityRegistration> findByActionIdAndStatusOrderByRegisteredAtAsc(long actionId, RegistrationStatus status);

    List<CommunityRegistration> findByUserIdAndStatusNot(long userId, RegistrationStatus status);

    List<CommunityRegistration> findByUserIdAndActionIdInAndStatusNot(long userId, Collection<Long> actionIds, RegistrationStatus status);

    /** Pentru `goingPreview`: înscrierile active la acțiunile date, cele mai noi primele, cu numele afișat. */
    @Query("""
            select r.actionId as actionId, u.displayName as displayName from CommunityRegistration r, aradexperience.entity.users.User u
            where u.id = r.userId and r.actionId in :actionIds
              and r.status in (aradexperience.enums.RegistrationStatus.REGISTERED, aradexperience.enums.RegistrationStatus.CONFIRMED)
            order by r.registeredAt desc, r.id desc""")
    List<GoingRow> going(@Param("actionIds") Collection<Long> actionIds);

    interface GoingRow {
        Long getActionId();
        String getDisplayName();
    }

    @Query("""
            select r.actionId as actionId, count(r) as pending from CommunityRegistration r
            where r.actionId in :actionIds and r.status = aradexperience.enums.RegistrationStatus.REGISTERED
            group by r.actionId""")
    List<PendingRow> pending(@Param("actionIds") Collection<Long> actionIds);

    interface PendingRow {
        Long getActionId();
        Long getPending();
    }

    /** Participările confirmate într-o perioadă (pentru coloanele „Acțiuni” și „Ultima participare” din clasament). */
    @Query("""
            select r.userId as userId, count(r) as actionsCount, max(r.confirmedAt) as lastAt from CommunityRegistration r
            where r.status = aradexperience.enums.RegistrationStatus.CONFIRMED and r.confirmedAt >= :from and r.confirmedAt < :to
            group by r.userId""")
    List<ParticipationRow> participation(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    interface ParticipationRow {
        Long getUserId();
        Long getActionsCount();
        OffsetDateTime getLastAt();
    }

    /** Titlul ultimei acțiuni confirmate a fiecăruia în perioadă: rândurile vin de la cel mai nou la cel mai vechi. */
    @Query("""
            select r.userId as userId, a.title as title from CommunityRegistration r, CommunityAction a
            where a.id = r.actionId and r.status = aradexperience.enums.RegistrationStatus.CONFIRMED
              and r.confirmedAt >= :from and r.confirmedAt < :to
            order by r.confirmedAt desc""")
    List<LastActionRow> lastActions(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    interface LastActionRow {
        Long getUserId();
        String getTitle();
    }

    /** Activitatea recentă din panoul Primăriei (înscrieri, confirmări, respingeri), cea mai nouă prima. */
    @Query("""
            select r from CommunityRegistration r where r.status <> aradexperience.enums.RegistrationStatus.CANCELLED
            order by coalesce(r.confirmedAt, r.registeredAt) desc, r.id desc""")
    List<CommunityRegistration> recent(Pageable page);
}
