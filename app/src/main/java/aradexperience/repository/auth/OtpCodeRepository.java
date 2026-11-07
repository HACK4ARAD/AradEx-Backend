package aradexperience.repository.auth;

import aradexperience.entity.auth.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    /**
     * Blochează numărul până la sfârșitul tranzacției (advisory lock Postgres), ca cererile și verificările în paralel
     * pentru același număr să se facă pe rând: limitele (60 s, 5 pe zi, 5 încercări) nu pot fi ocolite prin paralelism.
     */
    @Query(value = "SELECT CAST(pg_advisory_xact_lock(hashtext(:phone)) AS text)", nativeQuery = true)
    String lockPhone(@Param("phone") String phone);

    /** Ultimul cod cerut pentru număr (limita de 60 s și verificarea pentru numerele reale). */
    Optional<OtpCode> findFirstByPhoneOrderByCreatedAtDesc(String phone);

    /** Cel mai vechi cod din fereastra zilei (cât mai trebuie așteptat după 5 coduri). */
    Optional<OtpCode> findFirstByPhoneAndCreatedAtAfterOrderByCreatedAtAsc(String phone, Instant since);

    /** Câte coduri s-au cerut pentru număr de la un moment dat (limita pe zi). */
    long countByPhoneAndCreatedAtAfter(String phone, Instant since);

    /** Codurile nefolosite cerute de la un moment dat (limita pe zi: login-urile reușite nu contează). */
    long countByPhoneAndCreatedAtAfterAndConsumedAtIsNull(String phone, Instant since);

    /** Cel mai vechi cod nefolosit din fereastra zilei (cât mai trebuie așteptat după 5). */
    Optional<OtpCode> findFirstByPhoneAndCreatedAtAfterAndConsumedAtIsNullOrderByCreatedAtAsc(String phone, Instant since);

    /** Câte SMS-uri reale s-au trimis în total de la un moment dat (limita globală pe zi, contra SMS pumping). */
    long countByProviderInAndCreatedAtAfter(java.util.Collection<OtpCode.Provider> providers, Instant since);

    /** Codurile de demo încă valabile pentru număr (mai mulți oameni pot intra simultan pe același cont demo). */
    List<OtpCode> findTop10ByPhoneAndConsumedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(String phone, Instant now);
}
