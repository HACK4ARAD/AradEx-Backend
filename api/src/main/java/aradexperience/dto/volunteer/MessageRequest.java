package aradexperience.dto.volunteer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un mesaj nou în chatul de grup al unei acțiuni (POST /vol/actions/{id}/chat). */
public record MessageRequest(@NotBlank @Size(max = 1000) String text) {
}
