package aradexperience.entity.offers;

import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Tabelul `offers` (V7, T10): o ofertă a unei firme. `validUntil` null = permanentă. */
@Entity
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "discount_label", nullable = false, length = 30)
    private String discountLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OfferCategory category = OfferCategory.OTHER;

    @Column(name = "local_product", nullable = false)
    private boolean localProduct;

    /** V19: ofertă promovată — prima în lista publică, înaintea sortării după distanță sau dată (doar din seed). */
    @Column(nullable = false)
    private boolean featured;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "points_price")
    private Integer pointsPrice;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentStatus status = ContentStatus.PUBLISHED;

    @Column(name = "views_count", nullable = false)
    private int viewsCount;

    @Column(name = "redemptions_count", nullable = false)
    private int redemptionsCount;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Publicată și valabilă în ziua `today` (inclusiv capetele). */
    public boolean isAvailableOn(LocalDate today) {
        return status == ContentStatus.PUBLISHED && !today.isBefore(validFrom) && (validUntil == null || !today.isAfter(validUntil));
    }
}
