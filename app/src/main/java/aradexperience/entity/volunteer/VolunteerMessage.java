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

/** Tabelul `volunteer_messages` (V13): conversația unui voluntar cu Primăria; `readAt` = văzut de cealaltă parte. */
@Entity
@Table(name = "volunteer_messages")
@Getter
@Setter
@NoArgsConstructor
public class VolunteerMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volunteer_user_id", nullable = false)
    private Long volunteerUserId;

    @Column(name = "from_city", nullable = false)
    private boolean fromCity;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
