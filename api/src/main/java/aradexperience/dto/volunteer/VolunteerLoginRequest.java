package aradexperience.dto.volunteer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** POST /auth/volunteer: codul de 6 cifre primit „prin SMS” când Primăria a creat contul. */
public record VolunteerLoginRequest(@NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "the code has 6 digits") String code) {
}
