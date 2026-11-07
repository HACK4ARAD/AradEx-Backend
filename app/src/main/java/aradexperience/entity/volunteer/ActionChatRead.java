package aradexperience.entity.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Tabelul `action_chat_reads` (V15): până la ce mesaj a citit un voluntar chatul unei acțiuni. */
@Entity
@Table(name = "action_chat_reads")
@IdClass(ActionChatRead.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActionChatRead {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "action_id")
    private Long actionId;

    @Column(name = "last_read_id", nullable = false)
    private long lastReadId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.EqualsAndHashCode
    public static class Key implements Serializable {
        private Long userId;
        private Long actionId;
    }
}
