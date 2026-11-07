package aradexperience.dto.offers;

import aradexperience.enums.RedemptionStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * docs/API.md: RedemptionDto — codul `AX-O{offerId}-XXXXXX` (QR-ul conține exact codul). `pointsSpent` e 0 până la T11.
 * `offerImageUrl` + `validUntil` (T07): rândul din „Biletele mele” are poza ofertei, iar „Codul tău” arată „Valabilă până …”.
 */
public record RedemptionDto(Long id, String code, Long offerId, String offerTitle, String businessName, int pointsSpent,
                            RedemptionStatus status, OffsetDateTime issuedAt, OffsetDateTime usedAt,
                            String offerImageUrl, LocalDate validUntil) {
}
