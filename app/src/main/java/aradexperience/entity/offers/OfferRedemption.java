package aradexperience.entity.offers;

import aradexperience.enums.RedemptionStatus;
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

import java.time.OffsetDateTime;

/** Tabelul `offer_redemptions` (V7, T10): codul `AX-O{offerId}-XXXXXX` primit de un utilizator, folosit o singură dată la firmă. */
@Entity
@Table(name = "offer_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class OfferRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false)
    private Long offerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 24)
    private String code;

    @Column(name = "points_spent", nullable = false)
    private int pointsSpent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RedemptionStatus status = RedemptionStatus.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "used_by_user_id")
    private Long usedByUserId;
}
