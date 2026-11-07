package aradexperience.entity.points;

import aradexperience.enums.PointsType;
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

/** Tabelul `points_transactions` (V8). Scris DOAR de PointsService; `amount` > 0 câștig, < 0 cheltuială. */
@Entity
@Table(name = "points_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PointsTransaction {

    /** Valorile din coloana `ref_type`. */
    public static final String REF_ACTION = "ACTION";
    public static final String REF_OFFER = "OFFER";
    public static final String REF_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointsType type;

    @Column(name = "ref_type", length = 20)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(length = 250)
    private String note;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
