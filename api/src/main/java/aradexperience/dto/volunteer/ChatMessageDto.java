package aradexperience.dto.volunteer;

import java.time.OffsetDateTime;

/** Un mesaj din chatul de grup al unei acțiuni: text și / sau poză (`imageUrl`); `mine` = scris de voluntarul care citește. */
public record ChatMessageDto(Long id, long actionId, Long authorId, String authorName, boolean mine, String text, String imageUrl,
                             OffsetDateTime createdAt) {
}
