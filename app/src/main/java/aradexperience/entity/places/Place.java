package aradexperience.entity.places;

import aradexperience.enums.PlaceType;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Tabelul `places` (V3): obiectivele turistice. `tags` = csv („istorie,familie”). */
@Entity
@Table(name = "places")
@Getter
@Setter
@NoArgsConstructor
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceType type;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(length = 250)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_credit", length = 250)
    private String imageCredit;

    @Column(name = "opening_hours", columnDefinition = "text")
    private String openingHours;

    @Column(name = "visit_minutes", nullable = false)
    private int visitMinutes = 60;

    @Column(length = 250)
    private String tags;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "parking_hint", columnDefinition = "text")
    private String parkingHint;

    @Column(name = "exterior_only", nullable = false)
    private boolean exteriorOnly;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
