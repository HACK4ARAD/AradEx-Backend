package aradexperience.repository.places;

import aradexperience.entity.places.GuidePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GuidePointRepository extends JpaRepository<GuidePoint, Long> {

    List<GuidePoint> findByPlaceIdOrderByPositionAsc(Long placeId);

    Optional<GuidePoint> findByIdAndPlaceId(Long id, Long placeId);

    Optional<GuidePoint> findByPlaceIdAndPosition(Long placeId, int position);

    @Query("select coalesce(max(g.position), 0) from GuidePoint g where g.placeId = :placeId")
    int maxPosition(Long placeId);

    /** Numărul de puncte și secundele de citit, pe obiectiv — pentru badge-ul „Ghid · N puncte · M min” din liste. */
    @Query("select g.placeId as placeId, count(g) as count, coalesce(sum(g.durationSec), 0) as seconds from GuidePoint g group by g.placeId")
    List<GuideStats> stats();

    interface GuideStats {
        Long getPlaceId();
        Long getCount();
        Long getSeconds();
    }
}
