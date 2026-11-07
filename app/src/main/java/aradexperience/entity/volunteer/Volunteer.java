package aradexperience.entity.volunteer;

import aradexperience.enums.VolunteerStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tabelul `volunteers` (V13): contul unui voluntar, pe lângă rândul lui din `users` (rol VOLUNTEER, fără parolă). Acțiunile
 * alocate sunt în `volunteer_actions`. Telefonul stă aici, nu în `users.phone`, ca voluntarul să nu poată intra cu OTP (T21).
 */
@Entity
@Table(name = "volunteers")
@Getter
@Setter
@NoArgsConstructor
public class Volunteer {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 16)
    private String phone;

    @Column(length = 190)
    private String email;

    @Column(name = "login_code", nullable = false, unique = true, length = 6)
    private String loginCode;

    @Column(name = "one_time", nullable = false)
    private boolean oneTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private VolunteerStatus status = VolunteerStatus.ACTIVE;

    @Column(name = "coordinator_id", nullable = false)
    private Long coordinatorId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "volunteer_actions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "action_id")
    private Set<Long> actionIds = new LinkedHashSet<>();
}
