package aradexperience.repository.offers;

import aradexperience.entity.offers.Offer;
import aradexperience.enums.ContentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    /** Ofertele publice: publicate și valabile în `today`; filtrele (categorie, puncte, text, distanță) le aplică service-ul. */
    @Query("""
            select o from Offer o
            where o.status = aradexperience.enums.ContentStatus.PUBLISHED
              and o.validFrom <= :today and (o.validUntil is null or o.validUntil >= :today)
            order by o.createdAt desc, o.id desc""")
    List<Offer> findAvailable(@Param("today") LocalDate today);

    @Query("""
            select o from Offer o
            where o.businessId = :businessId and o.status = :status
              and o.validFrom <= :today and (o.validUntil is null or o.validUntil >= :today)
            order by o.id""")
    List<Offer> findAvailableByBusiness(@Param("businessId") Long businessId, @Param("status") ContentStatus status,
                                        @Param("today") LocalDate today);

    /** Panoul firmei: toate ofertele ei (fără cele ascunse la ștergere). */
    List<Offer> findByBusinessIdAndStatusNotOrderByIdDesc(Long businessId, ContentStatus status);

    @Query("select coalesce(sum(o.usedCount), 0) from Offer o where o.businessId = :businessId")
    long sumUsedByBusiness(@Param("businessId") Long businessId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Offer o set o.viewsCount = o.viewsCount + 1 where o.id = :id")
    int incrementViews(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Offer o set o.redemptionsCount = o.redemptionsCount + 1 where o.id = :id")
    int incrementRedemptions(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Offer o set o.usedCount = o.usedCount + 1 where o.id = :id")
    int incrementUsed(@Param("id") Long id);
}
