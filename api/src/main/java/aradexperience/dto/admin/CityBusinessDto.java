package aradexperience.dto.admin;

import java.time.OffsetDateTime;

/**
 * docs/API.md §Admin (T14): rândul din „Firme partenere” (`/city/businesses`) = BusinessSummary + contactul (proprietarul contului),
 * ofertele (active / ciornă), codurile folosite, data înregistrării și respingerea (null dacă nu a fost respinsă).
 */
public record CityBusinessDto(Long id, String name, String category, boolean verified, String imageUrl, String address, String phone,
                              String contactName, String email, long activeOffers, long draftOffers, long codesUsed,
                              OffsetDateTime createdAt, OffsetDateTime rejectedAt, String rejectReason) {
}
