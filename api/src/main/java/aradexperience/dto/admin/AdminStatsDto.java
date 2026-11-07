package aradexperience.dto.admin;

import aradexperience.enums.Role;

import java.util.Map;

/**
 * docs/API.md §Admin (T14): `GET /admin/stats`. `usersByRole` are toate rolurile (0 dacă lipsesc); `events`/`actions` = publicate;
 * `ticketsSold` = locuri (adulți + copii) neanulate; `offersRedeemed` = coduri revendicate, `codesUsed` = folosite la casă;
 * `pointsAwarded` = puncte câștigate la acțiuni.
 */
public record AdminStatsDto(Map<Role, Long> usersByRole, long events, long ticketsSold, long offersRedeemed, long codesUsed,
                            long pointsAwarded, long actions) {
}
