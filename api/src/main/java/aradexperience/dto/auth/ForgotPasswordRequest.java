package aradexperience.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /auth/password/forgot: adresa contului; răspunsul e același (204) și când adresa nu are cont. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 190) String email) {
}
