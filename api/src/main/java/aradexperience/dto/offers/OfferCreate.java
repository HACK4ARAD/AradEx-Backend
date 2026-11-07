package aradexperience.dto.offers;

import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * POST/PUT `/business/offers`. `category` lipsă = din categoria firmei; `validFrom` lipsă = azi; `validUntil` lipsă = permanentă;
 * `status` (opțional) = DRAFT („pauză”) sau PUBLISHED — firma neverificată rămâne oricum DRAFT. Limitele = coloanele din V7.
 */
public record OfferCreate(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank @Size(max = 30) String discountLabel,
        OfferCategory category,
        boolean localProduct,
        LocalDate validFrom,
        LocalDate validUntil,
        @Positive @Max(10000) Integer pointsPrice,
        @Size(max = 500) String imageUrl,
        ContentStatus status) {
}
