package aradexperience.dto.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * docs/API.md §Admin (T14): un rând din „Conținut & moderare” (`GET /admin/{events|offers|actions}`), același pentru toate
 * cele trei tipuri. `organizerVerified` e null când organizatorul nu e o firmă. Evenimente/acțiuni: `startAt`/`endAt`,
 * `participants`/`capacity`; oferte: `validFrom`/`validUntil`, `used`/`views`. `status`: DRAFT|PUBLISHED|HIDDEN (evenimente,
 * oferte) sau DRAFT|PUBLISHED|CANCELLED|DONE (acțiuni).
 */
public record AdminContentDto(Long id, String title, String category, String organizer, Long businessId, Boolean organizerVerified,
                              String imageUrl, OffsetDateTime startAt, OffsetDateTime endAt, LocalDate validFrom, LocalDate validUntil,
                              Integer participants, Integer capacity, Integer used, Integer views, String status) {
}
