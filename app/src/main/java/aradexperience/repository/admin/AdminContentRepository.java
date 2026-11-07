package aradexperience.repository.admin;

import aradexperience.entity.business.Business;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.events.Event;
import aradexperience.entity.offers.Offer;
import aradexperience.enums.ActionStatus;
import aradexperience.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

/**
 * T14: tabelele din „Conținut & moderare” și operațiile Primăriei/Adminului pe firmele partenere. Interoghează entitățile
 * altor story-uri (evenimente, oferte, acțiuni, anunțuri) fără să le modifice codul. `q` vine deja ca tipar `%text%` cu litere mici.
 */
public interface AdminContentRepository extends Repository<Business, Long> {

    @Query("""
            select e from Event e where e.status in :statuses and lower(e.title) like :q
              and (:all = true or coalesce(e.endAt, e.startAt) >= :now)""")
    Page<Event> events(@Param("statuses") Collection<ContentStatus> statuses, @Param("q") String q,
                       @Param("all") boolean all, @Param("now") OffsetDateTime now, Pageable page);

    @Query("select o from Offer o where o.status in :statuses and lower(o.title) like :q")
    Page<Offer> offers(@Param("statuses") Collection<ContentStatus> statuses, @Param("q") String q, Pageable page);

    @Query("""
            select a from CommunityAction a where a.status in :statuses and lower(a.title) like :q
              and (:all = true or a.endAt >= :now)""")
    Page<CommunityAction> actions(@Param("statuses") Collection<ActionStatus> statuses, @Param("q") String q,
                                  @Param("all") boolean all, @Param("now") OffsetDateTime now, Pageable page);

    @Query("select b from Business b where b.id in :ids")
    List<Business> businesses(@Param("ids") Collection<Long> ids);

    // ---------------------------------------------------------------- firme partenere

    @Query("select b from Business b order by b.verified asc, b.createdAt desc")
    List<Business> allBusinesses();

    @Query("""
            select o.businessId as businessId,
                   sum(case when o.status = :published then 1 else 0 end) as active,
                   sum(case when o.status = :draft then 1 else 0 end) as drafts,
                   sum(o.usedCount) as used
              from Offer o group by o.businessId""")
    List<OfferCounts> offerCounts(@Param("published") ContentStatus published, @Param("draft") ContentStatus draft);

    interface OfferCounts {
        Long getBusinessId();
        Long getActive();
        Long getDrafts();
        Long getUsed();
    }

    /** Verificare (DRAFT → PUBLISHED) sau suspendare (PUBLISHED → DRAFT) a conținutului unei firme. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Event e set e.status = :to where e.organizerBusinessId = :businessId and e.status = :from")
    int moveEvents(@Param("businessId") long businessId, @Param("from") ContentStatus from, @Param("to") ContentStatus to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Offer o set o.status = :to where o.businessId = :businessId and o.status = :from")
    int moveOffers(@Param("businessId") long businessId, @Param("from") ContentStatus from, @Param("to") ContentStatus to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Announcement a set a.status = :to where a.businessId = :businessId and a.status = :from")
    int moveAnnouncements(@Param("businessId") long businessId, @Param("from") ContentStatus from, @Param("to") ContentStatus to);
}
