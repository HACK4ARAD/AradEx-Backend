package aradexperience.repository.sms;

import aradexperience.entity.sms.SmsOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SmsOutboxRepository extends JpaRepository<SmsOutbox, Long> {

    /**
     * SMS-urile de dat telefonului acum: cele noi și cele luate dar neconfirmate de mult (`staleBefore`), încă în fereastra
     * de trimitere. `FOR UPDATE SKIP LOCKED`: două cereri în paralel (de ex. două scripturi pornite) nu iau același SMS.
     */
    @Query(value = """
            SELECT * FROM sms_outbox
            WHERE send_before > :now
              AND (status = 'PENDING' OR (status = 'CLAIMED' AND claimed_at < :staleBefore))
            ORDER BY created_at
            LIMIT :max
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SmsOutbox> lockClaimable(@Param("now") Instant now, @Param("staleBefore") Instant staleBefore, @Param("max") int max);

    /** Ce n-a plecat la timp nu mai pleacă: codul ar ajunge expirat. Textul (cu codul) se șterge. */
    @Modifying
    @Query("UPDATE SmsOutbox s SET s.status = aradexperience.entity.sms.SmsOutbox.Status.EXPIRED, s.body = null "
            + "WHERE s.status IN (aradexperience.entity.sms.SmsOutbox.Status.PENDING, aradexperience.entity.sms.SmsOutbox.Status.CLAIMED) AND s.sendBefore <= :now")
    int expireOld(@Param("now") Instant now);
}
