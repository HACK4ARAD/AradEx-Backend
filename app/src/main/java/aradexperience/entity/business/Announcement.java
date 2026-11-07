package aradexperience.entity.business;

import aradexperience.enums.ContentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** Tabelul `announcements` (V6, T09): anunțuri scurte ale unei firme (program, noutăți, angajări), fără dată de eveniment. */
@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentStatus status = ContentStatus.PUBLISHED;

    /** Setat de aplicație (nu de DB) ca să-l avem în răspunsul de creare fără un refresh. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
