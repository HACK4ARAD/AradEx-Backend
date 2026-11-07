package aradexperience.repository.business;

import aradexperience.entity.business.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByOwnerUserId(Long ownerUserId);

    /** +1 afișare pe pagina publică, atomic în DB (fără „lost update” la vizite simultane). Întoarce 0 dacă firma nu există. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Business b set b.viewsCount = b.viewsCount + 1 where b.id = :id")
    int incrementViews(@Param("id") Long id);
}
