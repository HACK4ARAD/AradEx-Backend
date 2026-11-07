package aradexperience.dto.community;

import aradexperience.enums.ActionCategory;

import java.time.OffsetDateTime;
import java.util.List;

/** docs/API.md: ActionSummary. `goingPreview` = prenumele ultimilor 3 înscriși; `myRegistration` doar pentru utilizatorul logat. */
public record ActionSummary(Long id, String title, ActionCategory category, String organizer, OffsetDateTime startAt, OffsetDateTime endAt,
                            String locationName, double lat, double lng, int pointsReward, int capacity, int registeredCount,
                            String imageUrl, List<String> goingPreview, RegistrationDto myRegistration) {
}
