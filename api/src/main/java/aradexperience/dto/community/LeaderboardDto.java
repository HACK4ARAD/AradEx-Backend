package aradexperience.dto.community;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * docs/API.md: LeaderboardDto. Doar puncte câștigate în perioadă (luna / anul curent, Europe/Bucharest); top 50 + poziția mea.
 * `actionsCount`, `lastAt`, `lastActionTitle` = participările confirmate în perioadă (coloanele „Acțiuni” și „Ultima participare”).
 */
public record LeaderboardDto(String period, List<Entry> entries, Me me) {

    public record Entry(int rank, Long userId, String displayName, int points, int actionsCount, OffsetDateTime lastAt, String lastActionTitle) {
    }

    /** `rank` = locul utilizatorului logat; dacă n-are puncte în perioadă, e primul loc după cei care au. */
    public record Me(int rank, int points) {
    }
}
