package aradexperience.repository.auth;

import aradexperience.entity.auth.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /**
     * Codurile încă deschise ale contului, blocate până la sfârșitul tranzacției (SELECT … FOR UPDATE), ca încercările în
     * paralel să nu ocolească limita de 5.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PasswordResetCode> findByUserIdAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(Long userId, Instant now);
}
