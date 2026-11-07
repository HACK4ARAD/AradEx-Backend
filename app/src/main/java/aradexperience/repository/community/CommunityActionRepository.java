package aradexperience.repository.community;

import aradexperience.entity.community.CommunityAction;
import aradexperience.enums.ActionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityActionRepository extends JpaRepository<CommunityAction, Long> {

    /** Acțiunile publicate care nu s-au încheiat, cele mai apropiate primele. */
    List<CommunityAction> findByStatusAndEndAtAfterOrderByStartAtAsc(ActionStatus status, OffsetDateTime now);

    List<CommunityAction> findByStatusInOrderByStartAtDesc(Collection<ActionStatus> statuses);

    List<CommunityAction> findAllByOrderByStartAtDesc();

    /**
     * Rândul acțiunii blocat până la finalul tranzacției (SELECT … FOR UPDATE): înscrierile, anulările și confirmările
     * la aceeași acțiune se serializează, deci capacitatea și contoarele nu pot fi depășite / dublate de cereri simultane.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CommunityAction a where a.id = :id")
    Optional<CommunityAction> findForUpdate(@Param("id") long id);
}
