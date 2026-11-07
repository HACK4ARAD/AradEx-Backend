package aradexperience.dto.volunteer;

/** GET /vol/me: antetul din „Acțiunile tale” — coordonatorul și contoarele (alocate, scanați de tine, de scanat), mesaje necitite. */
public record VolunteerMeDto(Long id, String name, String coordinatorName, boolean oneTime, int actions, long scanned, long toScan,
                             int unreadMessages) {
}
