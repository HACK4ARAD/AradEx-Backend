package aradexperience.dto.volunteer;

import java.time.OffsetDateTime;
import java.util.List;

/** Chatul de grup al unei acțiuni: antetul (titlu, voluntarii) și mesajele, de la cel mai vechi (ultimele 200). */
public record ChatDto(long actionId, String title, OffsetDateTime startAt, List<ChatMemberDto> members, List<ChatMessageDto> messages) {
}
