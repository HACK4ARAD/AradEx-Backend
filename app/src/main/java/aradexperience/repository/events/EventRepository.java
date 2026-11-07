package aradexperience.repository.events;

import aradexperience.entity.events.Event;
import aradexperience.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/** Evenimente (V4). T09 folosește doar interogările pe firmă; T06 adaugă lista publică cu filtre. */
public interface EventRepository extends JpaRepository<Event, Long> {

    /** Toate evenimentele unei firme (inclusiv ciorne și trecute), cele mai noi primele — panoul firmei. */
    List<Event> findByOrganizerBusinessIdOrderByStartAtDesc(Long businessId);

    /** Evenimentele care nu s-au terminat încă (încep după `now` sau sunt în desfășurare), în ordine cronologică. */
    @Query("""
            select e from Event e
            where e.organizerBusinessId = :businessId and e.status = :status
              and (e.startAt >= :now or e.endAt >= :now)
            order by e.startAt""")
    List<Event> findUpcomingByBusiness(@Param("businessId") Long businessId, @Param("status") ContentStatus status,
                                       @Param("now") OffsetDateTime now);

    @Query("""
            select count(e) from Event e
            where e.organizerBusinessId = :businessId and e.status <> aradexperience.enums.ContentStatus.HIDDEN
              and (e.startAt >= :now or e.endAt >= :now)""")
    long countUpcomingByBusiness(@Param("businessId") Long businessId, @Param("now") OffsetDateTime now);
}
