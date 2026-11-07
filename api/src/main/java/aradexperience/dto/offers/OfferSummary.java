package aradexperience.dto.offers;

import java.time.LocalDate;

/** docs/API.md: OfferSummary. Doar tipul, pentru pagina firmei din T09 (lista e goală până la T10, care adaugă ofertele). */
public record OfferSummary(Long id, Long businessId, String businessName, String title, String discountLabel, boolean localProduct,
                           LocalDate validUntil, Integer pointsPrice, String imageUrl, Double distanceKm, Double rating) {
}
