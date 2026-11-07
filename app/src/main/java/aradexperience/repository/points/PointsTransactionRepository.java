package aradexperience.repository.points;

import aradexperience.entity.points.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findTop50ByUserIdOrderByCreatedAtDescIdDesc(long userId);

    /** Clasamentul = suma punctelor câștigate (amount > 0) în perioadă, descrescător; egalitățile după id (stabil). */
    @Query("""
            select t.userId as userId, sum(t.amount) as points from PointsTransaction t
            where t.amount > 0 and t.createdAt >= :from and t.createdAt < :to
            group by t.userId order by sum(t.amount) desc, t.userId asc""")
    List<BoardRow> board(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    interface BoardRow {
        Long getUserId();
        Long getPoints();
    }

    /** Momentele în care utilizatorul a câștigat puncte (pentru seria de luni active). */
    @Query("select t.createdAt from PointsTransaction t where t.userId = :userId and t.amount > 0")
    List<OffsetDateTime> earnedAt(@Param("userId") long userId);

    /** Puncte câștigate la acțiuni în perioadă (KPI „Puncte acordate” din panoul Primăriei). */
    @Query("""
            select coalesce(sum(t.amount), 0) from PointsTransaction t
            where t.type = aradexperience.enums.PointsType.EARN_ACTION and t.createdAt >= :from and t.createdAt < :to""")
    long awardedBetween(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
