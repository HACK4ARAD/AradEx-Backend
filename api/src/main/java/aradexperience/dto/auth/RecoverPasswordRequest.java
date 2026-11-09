package aradexperience.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /auth/password/recover: „Am uitat parola” cu codul de recuperare primit la crearea contului (12 caractere,
 * `XXXX-XXXX-XXXX`; serverul ignoră majusculele, spațiile și liniuțele) + parola nouă (aceleași limite ca la înregistrare).
 */
public record RecoverPasswordRequest(
        @NotBlank @Email @Size(max = 190) String email,
        @NotBlank @Size(max = 32) String recoveryCode,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
