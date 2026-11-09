package aradexperience.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /auth/password/reset: codul de 6 cifre primit pe email + parola nouă (aceleași limite ca la înregistrare). */
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
