package aradexperience.dto.offers;

import aradexperience.enums.OfferCategory;

import java.time.LocalDate;

/**
 * docs/API.md: OfferSummary (cardul din tab-ul Oferte, pagina firmei). `validUntil` null = permanentă; `distanceKm` doar cu `near`;
 * `rating` vine în T17. `category` adăugat în T10 (cercurile Cafenele / Restaurante / Făcut în Arad / Evenimente).
 */
public record OfferSummary(Long id, Long businessId, String businessName, String title, String discountLabel, boolean localProduct,
                           LocalDate validUntil, Integer pointsPrice, String imageUrl, Double distanceKm, Double rating,
                           OfferCategory category) {
}
