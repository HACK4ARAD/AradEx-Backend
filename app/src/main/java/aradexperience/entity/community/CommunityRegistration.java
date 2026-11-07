package aradexperience.entity.community;

import aradexperience.enums.RegistrationStatus;
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

/** Tabelul `community_registrations` (V8): o înscriere per (acțiune, utilizator); `code` = conținutul QR-ului (`AX-C{actionId}-XXXXXX`). */
@Entity
@Table(name = "community_registrations")
@Getter
@Setter
@NoArgsConstructor
public class CommunityRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.REGISTERED;

    @Column(name = "registered_at", nullable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** Cine a confirmat (Primăria sau, din T18, voluntarul) — „cine a scanat pe cine”. */
    @Column(name = "confirmed_by")
    private Long confirmedBy;
}
