package aradexperience.repository.auth;

import aradexperience.entity.auth.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /** Ultimul cod cerut pentru număr (pentru limita de 60 s și pentru verificare). */
    Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    /** Câte coduri s-au cerut pentru număr de la un moment dat (limita pe zi). */
    long countByPhoneAndCreatedAtAfter(String phone, Instant since);
}
