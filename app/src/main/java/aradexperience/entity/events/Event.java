package aradexperience.entity.events;

import aradexperience.enums.ContentStatus;
import aradexperience.enums.EventCategory;
import aradexperience.enums.OrganizerType;
import aradexperience.enums.TicketType;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tabelul `events` (V4). Introdus de T09 (evenimentele firmelor, `/business/events`); T06 îl folosește pentru lista publică.
 * FK-urile sunt simple `Long` (nu avem nevoie de obiecte).
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "organizer_type", nullable = false, length = 20)
    private OrganizerType organizerType;

    @Column(name = "organizer_business_id")
    private Long organizerBusinessId;

    @Column(name = "organizer_name", nullable = false, length = 160)
    private String organizerName;

    @Column(name = "place_id")
    private Long placeId;

    @Column(name = "location_name", nullable = false, length = 160)
    private String locationName;

    @Column(length = 250)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_type", nullable = false, length = 10)
    private TicketType ticketType;

    @Column(name = "price_from", precision = 10, scale = 2)
    private BigDecimal priceFrom;

    @Column(name = "price_adult", precision = 10, scale = 2)
    private BigDecimal priceAdult;

    @Column(name = "price_child", precision = 10, scale = 2)
    private BigDecimal priceChild;

    @Column(name = "external_url", length = 500)
    private String externalUrl;

    private Integer capacity;

    @Column(name = "participants_count", nullable = false)
    private int participantsCount;

    @Column(name = "saves_count", nullable = false)
    private int savesCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ContentStatus status = ContentStatus.PUBLISHED;

    @Column(name = "is_demo", nullable = false)
    private boolean demo;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
