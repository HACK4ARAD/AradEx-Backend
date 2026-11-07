package aradexperience.dto.points;

import aradexperience.enums.PointsType;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * docs/API.md: PointsDto. `rank` = locul în clasamentul lunii; `streakMonths` = luni consecutive cu puncte câștigate („Activ 2 luni la rând”).
 * `transactions` = ultimele 50, cele mai noi primele.
 */
public record PointsDto(int balance, int monthEarned, int rank, int streakMonths, List<Transaction> transactions) {

    public record Transaction(Long id, int amount, PointsType type, String note, OffsetDateTime createdAt) {
    }
}
