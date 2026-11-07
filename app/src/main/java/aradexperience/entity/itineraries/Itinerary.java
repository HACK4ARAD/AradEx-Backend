package aradexperience.entity.itineraries;

import aradexperience.enums.TransportMode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Tabelul `itineraries` (V16, T13). `stops` = JSON-ul listei `ItineraryStop` (îl scrie și îl citește `ItineraryMapper`). */
@Entity
@Table(name = "itineraries")
@Getter
@Setter
@NoArgsConstructor
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "share_code", nullable = false, unique = true, length = 16)
    private String shareCode;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(length = 300)
    private String ask;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int days = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransportMode transport;

    @Column(name = "start_time", length = 5)
    private String startTime;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    @Column(name = "total_km", nullable = false, precision = 6, scale = 1)
    private BigDecimal totalKm = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean offline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String stops;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
