package aradexperience.repository.users;

import aradexperience.entity.users.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Emailurile sunt stocate normalizate (trim + lowercase, vezi AuthService.normalize), deci căutarea e exactă și folosește
 * indexul UNIQUE(users.email). Orice cod care creează utilizatori (seed, Primăria în T18) scrie emailul tot normalizat.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** T21: conturile personale, după telefonul E.164. */
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
