package aradexperience.repository.offers;

import aradexperience.entity.offers.OfferRedemption;
import aradexperience.enums.RedemptionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OfferRedemptionRepository extends JpaRepository<OfferRedemption, Long> {

    Optional<OfferRedemption> findByCode(String code);

    boolean existsByOfferIdAndUserIdAndStatus(Long offerId, Long userId, RedemptionStatus status);

    /** Codurile mele, cele mai noi primele. */
    List<OfferRedemption> findByUserIdOrderByIssuedAtDesc(Long userId);

    /** Ultima revendicare a utilizatorului pentru o ofertă (pentru `myRedemption`). */
    Optional<OfferRedemption> findFirstByOfferIdAndUserIdOrderByIssuedAtDesc(Long offerId, Long userId);

    /** Codurile unei firme (toate ofertele ei), opțional pe stare; cele mai noi primele. */
    @Query("""
            select r from OfferRedemption r
            where r.offerId in (select o.id from Offer o where o.businessId = :businessId)
              and (:status is null or r.status = :status)
            order by coalesce(r.usedAt, r.issuedAt) desc""")
    List<OfferRedemption> findByBusiness(@Param("businessId") Long businessId, @Param("status") RedemptionStatus status, Pageable page);

    /** ISSUED → USED atomic: întoarce 0 dacă altcineva l-a folosit între timp (două scanări simultane). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update OfferRedemption r set r.status = aradexperience.enums.RedemptionStatus.USED, r.usedAt = :at, r.usedByUserId = :by
            where r.id = :id and r.status = aradexperience.enums.RedemptionStatus.ISSUED""")
    int markUsed(@Param("id") Long id, @Param("at") OffsetDateTime at, @Param("by") Long usedByUserId);

    boolean existsByOfferId(Long offerId);
}
