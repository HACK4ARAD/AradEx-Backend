package aradexperience.entity.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** Tabelul `saved_events` (V4): PK (user_id, event_id). */
@Entity
@Table(name = "saved_events")
@IdClass(SavedEvent.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class SavedEvent {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public SavedEvent(Long userId, Long eventId) {
        this.userId = userId;
        this.eventId = eventId;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long userId;
        private Long eventId;
    }
}
