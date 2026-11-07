package aradexperience.dto.offers;

import aradexperience.enums.RedemptionStatus;

import java.time.OffsetDateTime;

/** docs/API.md: RedemptionDto — codul `AX-O{offerId}-XXXXXX` (QR-ul conține exact codul). `pointsSpent` e 0 până la T11. */
public record RedemptionDto(Long id, String code, Long offerId, String offerTitle, String businessName, int pointsSpent,
                            RedemptionStatus status, OffsetDateTime issuedAt, OffsetDateTime usedAt) {
}
