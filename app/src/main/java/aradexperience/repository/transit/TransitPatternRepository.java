package aradexperience.repository.transit;

import aradexperience.entity.transit.TransitPattern;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitPatternRepository extends JpaRepository<TransitPattern, Integer> {
}
