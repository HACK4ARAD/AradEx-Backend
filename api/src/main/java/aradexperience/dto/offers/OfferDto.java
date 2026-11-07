package aradexperience.dto.offers;

import aradexperience.enums.ContentStatus;
import aradexperience.enums.OfferCategory;

import java.time.LocalDate;

/** docs/API.md: OfferDto = OfferSummary + detaliile + statistici; `myRedemption` = ultimul meu cod pentru ofertă (dacă sunt logat). */
public record OfferDto(Long id, Long businessId, String businessName, String title, String discountLabel, boolean localProduct,
                       LocalDate validUntil, Integer pointsPrice, String imageUrl, Double distanceKm, Double rating,
                       OfferCategory category, String description, LocalDate validFrom, ContentStatus status, int viewsCount,
                       int redemptionsCount, int usedCount, RedemptionDto myRedemption) {
}
