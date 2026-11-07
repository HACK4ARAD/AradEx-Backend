package aradexperience.dto.community;

import aradexperience.enums.ActionCategory;
import aradexperience.enums.ActionStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** docs/API.md: ActionDto = ActionSummary + { description, address, status, confirmedCount, imageCredit }. */
public record ActionDto(Long id, String title, ActionCategory category, String organizer, OffsetDateTime startAt, OffsetDateTime endAt,
                        String locationName, double lat, double lng, int pointsReward, int capacity, int registeredCount,
                        String imageUrl, List<String> goingPreview, RegistrationDto myRegistration,
                        String description, String address, ActionStatus status, int confirmedCount, String imageCredit) {
}
