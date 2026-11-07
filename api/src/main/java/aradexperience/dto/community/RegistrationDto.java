package aradexperience.dto.community;

import aradexperience.enums.RegistrationStatus;

import java.time.OffsetDateTime;

/**
 * docs/API.md: RegistrationDto. `user` doar în listele Primăriei; `confirmedBy` = numele celui care a confirmat (Primărie / voluntar).
 * `actionStartAt`, `locationName`, `pointsReward` ca biletul de acțiune să se afișeze fără o cerere în plus.
 */
public record RegistrationDto(Long id, Long actionId, String actionTitle, OffsetDateTime actionStartAt, String locationName, int pointsReward,
                              String code, RegistrationStatus status, OffsetDateTime registeredAt, OffsetDateTime confirmedAt,
                              String confirmedBy, UserRef user) {

    public record UserRef(Long id, String displayName) {
    }
}
