package aradexperience.dto.community;

import aradexperience.enums.RegistrationStatus;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /city/dashboard (Panoul Primăriei, W2): KPI-urile, participarea la acțiunile care urmează și activitatea recentă.
 * `pointsAwarded` = puncte câștigate la acțiuni în luna curentă; `activeVolunteers` = conturi VOLUNTEER (0 până la T18).
 */
public record CityDashboardDto(long upcomingEvents, long registrations, long pointsAwarded, long pendingValidations, long activeVolunteers,
                               List<ActionProgress> actions, List<Activity> activity) {

    public record ActionProgress(Long id, String title, OffsetDateTime startAt, int registeredCount, int capacity, long pendingCount, String imageUrl) {
    }

    /** O înscriere / confirmare / respingere recentă; textul îl compune FE (i18n). */
    public record Activity(RegistrationStatus type, String userName, String actionTitle, int points, String by, OffsetDateTime at) {
    }
}
