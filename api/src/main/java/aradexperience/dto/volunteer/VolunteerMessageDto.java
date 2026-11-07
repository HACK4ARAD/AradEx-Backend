package aradexperience.dto.volunteer;

import java.time.OffsetDateTime;

/** Un mesaj: `fromCity` = scris din panoul Primăriei (`authorName` = cine), altfel de voluntar; `readAt` = văzut de cealaltă parte. */
public record VolunteerMessageDto(Long id, boolean fromCity, String authorName, String text, OffsetDateTime createdAt, OffsetDateTime readAt) {
}
