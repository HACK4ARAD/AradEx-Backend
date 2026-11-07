package aradexperience.mapper.points;

import aradexperience.dto.community.LeaderboardDto;
import aradexperience.dto.points.PointsDto;
import aradexperience.entity.points.PointsTransaction;
import aradexperience.service.points.PointsService;
import lombok.experimental.UtilityClass;

/** PointsService.Summary / Board → PointsDto / LeaderboardDto. */
@UtilityClass
public class PointsMapper {

    public static PointsDto toDto(PointsService.Summary s) {
        return new PointsDto(s.balance(), s.monthEarned(), s.rank(), s.streakMonths(),
                s.transactions().stream().map(PointsMapper::toTransaction).toList());
    }

    public static PointsDto.Transaction toTransaction(PointsTransaction t) {
        return new PointsDto.Transaction(t.getId(), t.getAmount(), t.getType(), t.getNote(), t.getCreatedAt());
    }

    public static LeaderboardDto toLeaderboard(PointsService.Board b) {
        return new LeaderboardDto(b.period().key(), b.entries(), b.me());
    }
}
