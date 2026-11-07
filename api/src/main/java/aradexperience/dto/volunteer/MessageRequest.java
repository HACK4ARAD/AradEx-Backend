package aradexperience.dto.volunteer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un mesaj nou în conversația voluntar ↔ coordonator (POST /vol/messages, POST /city/volunteers/{id}/messages). */
public record MessageRequest(@NotBlank @Size(max = 1000) String text) {
}
