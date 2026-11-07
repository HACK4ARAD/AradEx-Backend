package aradexperience.repository.itineraries;

import aradexperience.entity.itineraries.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Itinerariile salvate (V16, T13). */
public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {

    Optional<Itinerary> findByShareCode(String shareCode);

    boolean existsByShareCode(String shareCode);

    List<Itinerary> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
