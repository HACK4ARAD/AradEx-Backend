package aradexperience.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /me/recovery-code: parola contului, ca un token furat să nu poată schimba codul de recuperare. */
public record RecoveryCodeRequest(@NotBlank @Size(max = 72) String password) {
}
