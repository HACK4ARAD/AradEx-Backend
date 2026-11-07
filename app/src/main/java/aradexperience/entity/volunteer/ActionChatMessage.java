package aradexperience.entity.volunteer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Tabelul `action_chat_messages` (V15): un mesaj din chatul de grup al voluntarilor unei acțiuni — text și / sau poză. */
@Entity
@Table(name = "action_chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ActionChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(length = 1000)
    private String text;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
