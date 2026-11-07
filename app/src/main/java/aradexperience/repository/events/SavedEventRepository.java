package aradexperience.repository.events;

import aradexperience.entity.events.SavedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SavedEventRepository extends JpaRepository<SavedEvent, SavedEvent.Key> {

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    @Modifying
    @Query("delete from SavedEvent s where s.userId = :userId and s.eventId = :eventId")
    int deleteOne(@Param("userId") Long userId, @Param("eventId") Long eventId);

    @Query("select s.eventId from SavedEvent s where s.userId = :userId")
    Set<Long> findEventIds(@Param("userId") Long userId);

    @Query("select s.eventId from SavedEvent s where s.userId = :userId and s.eventId in :eventIds")
    Set<Long> findEventIdsIn(@Param("userId") Long userId, @Param("eventIds") Collection<Long> eventIds);

    List<SavedEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
}
