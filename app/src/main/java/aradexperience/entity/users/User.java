package aradexperience.entity.users;

import aradexperience.enums.Role;
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

/** Tabelul `users` (V2, V11, V13, V20). Parola doar ca hash BCrypt. Conturile personale au telefon (E.164), celelalte email + parolă. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 190)
    private String email;

    /** E.164 (+40…); conturile personale intră cu el (T21). */
    @Column(unique = true, length = 16)
    private String phone;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 5)
    private String locale = "ro";

    @Column(name = "points_balance", nullable = false)
    private int pointsBalance;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** T14 (V13): contul dezactivat de Admin nu mai poate intra (login cu parolă sau cu cod). */
    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    /** V20 (GDPR): când a bifat Termenii și condițiile + Confidențialitatea și ce versiune; null = cont creat înainte / de Primărie. */
    @Column(name = "terms_accepted_at")
    private OffsetDateTime termsAcceptedAt;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    public boolean isDisabled() {
        return disabledAt != null;
    }
}
