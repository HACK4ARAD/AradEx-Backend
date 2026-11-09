package aradexperience.dto.auth;

/** Codul de recuperare nou, arătat o singură dată (`XXXX-XXXX-XXXX`); cel vechi nu mai merge. */
public record RecoveryCodeResponse(String recoveryCode) {
}
