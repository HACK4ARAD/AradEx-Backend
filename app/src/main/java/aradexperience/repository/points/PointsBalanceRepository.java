package aradexperience.repository.points;

import aradexperience.entity.users.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Singurul loc care scrie `users.points_balance`, folosit doar de PointsService. Update-uri atomice în SQL (nu read-modify-write),
 * deci două tranzacții simultane nu pot pierde puncte, iar `subtract` nu lasă soldul să scadă sub zero.
 */
public interface PointsBalanceRepository extends Repository<User, Long> {

    @Modifying(flushAutomatically = true)
    @Query("update User u set u.pointsBalance = u.pointsBalance + :amount where u.id = :userId")
    int add(@Param("userId") long userId, @Param("amount") int amount);

    /** @return 0 dacă utilizatorul nu există sau soldul e mai mic decât `amount` */
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.pointsBalance = u.pointsBalance - :amount where u.id = :userId and u.pointsBalance >= :amount")
    int subtract(@Param("userId") long userId, @Param("amount") int amount);
}
