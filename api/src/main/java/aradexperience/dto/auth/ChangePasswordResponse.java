package aradexperience.dto.auth;

/** Răspunsul la POST /me/password: un cod de recuperare nou (după o parolă temporară sau dacă contul n-avea cod), altfel null. */
public record ChangePasswordResponse(String recoveryCode) {
}
