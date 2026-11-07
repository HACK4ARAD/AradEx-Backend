package aradexperience.dto.community;

import aradexperience.enums.RegistrationStatus;

import java.time.OffsetDateTime;

/**
 * docs/API.md: ConfirmResultDto = RegistrationDto + { alreadyConfirmed, pointsAwarded }. Idempotent: a doua confirmare
 * întoarce `alreadyConfirmed: true`, `pointsAwarded: 0`.
 */
public record ConfirmResultDto(Long id, Long actionId, String actionTitle, OffsetDateTime actionStartAt, String locationName, int pointsReward,
                               String code, RegistrationStatus status, OffsetDateTime registeredAt, OffsetDateTime confirmedAt,
                               String confirmedBy, RegistrationDto.UserRef user, boolean alreadyConfirmed, int pointsAwarded) {
}
