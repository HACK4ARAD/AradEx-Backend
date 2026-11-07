package aradexperience.dto.volunteer;

import java.time.OffsetDateTime;

/** Un chat din lista „Mesaje”: acțiunea, câți voluntari sunt în el, ultimul mesaj și câte mesaje noi are. */
public record ChatSummaryDto(long actionId, String title, OffsetDateTime startAt, int members, ChatMessageDto lastMessage, int unread) {
}
