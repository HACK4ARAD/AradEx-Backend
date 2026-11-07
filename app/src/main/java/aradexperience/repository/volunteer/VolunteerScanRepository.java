package aradexperience.repository.volunteer;

import aradexperience.entity.community.CommunityRegistration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * „Cine a scanat pe cine” (T18), citit din înscrierile T11 (`community_registrations.confirmed_by`), fără să le modifice:
 * câte confirmări a făcut fiecare voluntar, pe acțiune, și ultimele lui.
 */
public interface VolunteerScanRepository extends Repository<CommunityRegistration, Long> {

    @Query("""
            select r.confirmedBy as userId, count(r) as scans from CommunityRegistration r
            where r.status = aradexperience.enums.RegistrationStatus.CONFIRMED and r.confirmedBy in :userIds group by r.confirmedBy""")
    List<ScanCount> scansBy(@Param("userIds") Collection<Long> userIds);

    @Query("""
            select r.actionId as actionId, count(r) as scans from CommunityRegistration r
            where r.status = aradexperience.enums.RegistrationStatus.CONFIRMED and r.confirmedBy = :userId and r.actionId in :actionIds
            group by r.actionId""")
    List<ActionScanCount> scansByActions(@Param("userId") long userId, @Param("actionIds") Collection<Long> actionIds);

    @Query("""
            select r from CommunityRegistration r
            where r.status = aradexperience.enums.RegistrationStatus.CONFIRMED and r.confirmedBy = :userId order by r.confirmedAt desc""")
    List<CommunityRegistration> recentBy(@Param("userId") long userId, Pageable page);

    interface ScanCount {
        Long getUserId();

        long getScans();
    }

    interface ActionScanCount {
        Long getActionId();

        long getScans();
    }
}
