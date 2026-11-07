package aradexperience.repository.events;

import aradexperience.entity.events.Event;
import aradexperience.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

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

    /** T06: candidații pentru GET /events — statusurile cerute și ce nu s-a terminat înainte de `endsAfter`; filtrele fine sunt în service. */
    @Query("select e from Event e where e.status in :statuses and coalesce(e.endAt, e.startAt) >= :endsAfter order by e.startAt, e.id")
    List<Event> findCandidates(@Param("statuses") Collection<ContentStatus> statuses, @Param("endsAfter") OffsetDateTime endsAfter);

    List<Event> findByIdInAndStatusOrderByStartAtAsc(Collection<Long> ids, ContentStatus status);

    /** T06: datele obiectivului legat (entitatea `Place` e a lui T05; aici doar citim ce se copiază pe eveniment). */
    @Query(value = "select name as \"name\", address as \"address\", lat as \"lat\", lng as \"lng\", image_url as \"imageUrl\", "
            + "image_credit as \"imageCredit\" from places where id = :id", nativeQuery = true)
    Optional<PlaceRef> findPlace(@Param("id") long id);

    interface PlaceRef {
        String getName();
        String getAddress();
        Double getLat();
        Double getLng();
        String getImageUrl();
        String getImageCredit();
    }
}
