package aradexperience.repository.business;

import aradexperience.entity.business.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByOwnerUserId(Long ownerUserId);
}
