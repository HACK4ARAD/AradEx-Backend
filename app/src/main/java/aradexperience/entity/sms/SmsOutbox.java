package aradexperience.entity.sms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Tabelul `sms_outbox` (V14): un SMS de trimis de pe telefonul gateway-ului (T24). `body` = null după trimitere. */
@Entity
@Table(name = "sms_outbox")
@Getter
@Setter
@NoArgsConstructor
public class SmsOutbox {

    public enum Status { PENDING, CLAIMED, SENT, FAILED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(length = 200)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int tries;

    @Column(name = "send_before", nullable = false)
    private Instant sendBefore;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
