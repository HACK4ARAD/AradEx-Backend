package aradexperience.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Un mesaj din conversație (T12): `user` = omul, `assistant` = răspunsurile anterioare ale asistentului. */
public record ChatMessageDto(
        @NotBlank @Pattern(regexp = "user|assistant") String role,
        @NotBlank @Size(max = 1000) String content) {
}
