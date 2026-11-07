package aradexperience.repository.volunteer;

import aradexperience.entity.volunteer.ActionChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ActionChatMessageRepository extends JpaRepository<ActionChatMessage, Long> {

    /** Ultimele mesaje ale unui chat, de la cel mai nou (le întoarce serviciul în ordine cronologică). */
    List<ActionChatMessage> findByActionIdOrderByIdDesc(long actionId, Pageable page);

    /** Ultimul mesaj din fiecare chat (lista „Mesaje”). */
    @Query("""
            select m from ActionChatMessage m where m.id in
            (select max(m2.id) from ActionChatMessage m2 where m2.actionId in :actionIds group by m2.actionId)""")
    List<ActionChatMessage> lastPerAction(@Param("actionIds") Collection<Long> actionIds);

    /** Mesajele altora, mai noi decât ce a citit voluntarul, pe chat. */
    @Query("""
            select m.actionId as actionId, count(m) as unread from ActionChatMessage m
            where m.actionId in :actionIds and m.authorId <> :userId
              and m.id > coalesce((select r.lastReadId from ActionChatRead r where r.userId = :userId and r.actionId = m.actionId), 0)
            group by m.actionId""")
    List<UnreadRow> unread(@Param("userId") long userId, @Param("actionIds") Collection<Long> actionIds);

    interface UnreadRow {
        Long getActionId();

        long getUnread();
    }
}
