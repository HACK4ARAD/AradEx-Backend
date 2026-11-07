package aradexperience.dto.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** POST /ai/chat (T12): istoricul conversației (ultimul mesaj e al omului) + limba răspunsului (`ro` implicit). */
public record ChatRequest(
        @NotEmpty @Size(max = 30) List<@Valid ChatMessageDto> messages,
        @Pattern(regexp = "ro|en") String locale) {
}
