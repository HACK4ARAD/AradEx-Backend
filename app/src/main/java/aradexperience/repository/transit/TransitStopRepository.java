package aradexperience.repository.transit;

import aradexperience.entity.transit.TransitStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitStopRepository extends JpaRepository<TransitStop, Integer> {
}
