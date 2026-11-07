package aradexperience.dto.business;

import aradexperience.enums.ContentStatus;

import java.time.OffsetDateTime;

/** docs/API.md: AnnouncementDto { id, title, body, imageUrl?, createdAt } + `status` (T09: ciornă cât timp firma nu e verificată). */
public record AnnouncementDto(Long id, String title, String body, String imageUrl, ContentStatus status, OffsetDateTime createdAt) {
}
