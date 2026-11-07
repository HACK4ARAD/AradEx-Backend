package aradexperience.dto.volunteer;

import aradexperience.enums.VolunteerStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Un voluntar în panoul Primăriei. `loginCode` = codul de 6 cifre; `scans` = câți participanți a confirmat; `expired` = cont de
 * unică folosință după ultima acțiune alocată.
 */
public record VolunteerDto(Long id, String name, String phone, String email, String loginCode, boolean oneTime, VolunteerStatus status,
                           boolean expired, Long coordinatorId, String coordinatorName, List<Long> actionIds, long scans,
                           OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
}
