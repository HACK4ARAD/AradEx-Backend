package aradexperience.repository.transit;

import aradexperience.entity.transit.TransitRoute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitRouteRepository extends JpaRepository<TransitRoute, Integer> {
}
