package aradexperience.repository.volunteer;

import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {

    Optional<Volunteer> findByLoginCode(String loginCode);

    boolean existsByLoginCode(String loginCode);

    List<Volunteer> findAllByOrderByCreatedAtDesc();

    /** Coordonatorii posibili: conturile Primăriei (CITY_HALL), după nume. */
    @Query("select u from User u where u.role = aradexperience.enums.Role.CITY_HALL order by u.displayName")
    List<User> coordinators();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Volunteer v where v.userId = :id")
    Optional<Volunteer> findForUpdate(@Param("id") long id);
}
