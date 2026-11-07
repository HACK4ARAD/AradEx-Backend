package aradexperience.dto.volunteer;

import aradexperience.enums.ActionCategory;
import aradexperience.enums.VolunteerActionState;

import java.time.OffsetDateTime;

/**
 * O acțiune alocată voluntarului (GET /vol/actions). `state` + `opensAt` (start − 30 min) pentru lacăt; `registered` = înscriși
 * activi, `confirmed` = confirmați (bifa verde când sunt egali și > 0); `confirmedByMe` = câți a scanat el aici.
 */
public record VolunteerActionDto(Long id, String title, ActionCategory category, OffsetDateTime startAt, OffsetDateTime endAt,
                                 String locationName, String address, double lat, double lng, int pointsReward, String imageUrl,
                                 int registered, int confirmed, long confirmedByMe, VolunteerActionState state, OffsetDateTime opensAt) {
}
