package aradexperience.repository.admin;

import aradexperience.entity.users.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/** T14: cifrele din `GET /admin/stats`, numărate direct în DB (tabelele altor story-uri, doar citire). */
public interface AdminStatsRepository extends Repository<User, Long> {

    @Query(value = "SELECT role AS role, COUNT(*) AS total FROM users GROUP BY role", nativeQuery = true)
    List<RoleCount> usersByRole();

    interface RoleCount {
        String getRole();
        Long getTotal();
    }

    @Query(value = "SELECT COUNT(*) FROM events WHERE status = 'PUBLISHED'", nativeQuery = true)
    long publishedEvents();

    /** Locuri vândute sau rezervate (adulți + copii), fără biletele anulate. */
    @Query(value = "SELECT COALESCE(SUM(qty_adult + qty_child), 0) FROM tickets WHERE status <> 'CANCELLED'", nativeQuery = true)
    long ticketsSold();

    /** Coduri de ofertă revendicate (emise sau folosite), fără cele anulate. */
    @Query(value = "SELECT COUNT(*) FROM offer_redemptions WHERE status <> 'CANCELLED'", nativeQuery = true)
    long offersRedeemed();

    @Query(value = "SELECT COUNT(*) FROM offer_redemptions WHERE status = 'USED'", nativeQuery = true)
    long codesUsed();

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM points_transactions WHERE type = 'EARN_ACTION'", nativeQuery = true)
    long pointsAwarded();

    @Query(value = "SELECT COUNT(*) FROM community_actions WHERE status = 'PUBLISHED'", nativeQuery = true)
    long publishedActions();
}
