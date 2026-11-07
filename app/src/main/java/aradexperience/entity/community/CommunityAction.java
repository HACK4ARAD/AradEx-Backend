package aradexperience.entity.community;

import aradexperience.enums.ActionCategory;
import aradexperience.enums.ActionStatus;
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

/** Tabelul `community_actions` (V8). `registeredCount` = înscrieri REGISTERED + CONFIRMED; contoarele le ține CommunityService. */
@Entity
@Table(name = "community_actions")
@Getter
@Setter
@NoArgsConstructor
public class CommunityAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionCategory category;

    @Column(length = 120)
    private String organizer;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "location_name", nullable = false, length = 200)
    private String locationName;

    @Column(length = 250)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "points_reward", nullable = false)
    private int pointsReward;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "registered_count", nullable = false)
    private int registeredCount;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_credit", length = 250)
    private String imageCredit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActionStatus status = ActionStatus.PUBLISHED;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
