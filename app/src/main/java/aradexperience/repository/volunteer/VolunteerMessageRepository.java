package aradexperience.repository.volunteer;

import aradexperience.entity.volunteer.VolunteerMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface VolunteerMessageRepository extends JpaRepository<VolunteerMessage, Long> {

    List<VolunteerMessage> findByVolunteerUserIdOrderByCreatedAtAscIdAsc(long volunteerUserId);

    /** Câte mesaje ale unei părți n-a văzut încă cealaltă (fromCity = true → necitite de voluntar). */
    int countByVolunteerUserIdAndFromCityAndReadAtIsNull(long volunteerUserId, boolean fromCity);

    /** Marchează ca citite mesajele unei părți, când cealaltă deschide conversația. */
    @Modifying
    @Query("update VolunteerMessage m set m.readAt = :now where m.volunteerUserId = :vol and m.fromCity = :fromCity and m.readAt is null")
    int markRead(@Param("vol") long volunteerUserId, @Param("fromCity") boolean fromCity, @Param("now") OffsetDateTime now);

    /** Mesajele voluntarilor necitite de Primărie, pe voluntar. */
    @Query("""
            select m.volunteerUserId as volunteerId, count(m) as unread from VolunteerMessage m
            where m.fromCity = false and m.readAt is null group by m.volunteerUserId""")
    List<UnreadRow> unreadByCity();

    interface UnreadRow {
        Long getVolunteerId();

        long getUnread();
    }

    /** Ultimul mesaj din fiecare conversație (lista din „Mesaje”). */
    @Query("""
            select m from VolunteerMessage m where m.id in
            (select max(m2.id) from VolunteerMessage m2 group by m2.volunteerUserId)""")
    List<VolunteerMessage> lastPerVolunteer();
}
