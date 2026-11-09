package aradexperience.dto.auth;

import aradexperience.enums.Role;

/**
 * docs/API.md: UserDto. `business` e null pentru rolurile fără firmă; `phone` (E.164) doar la conturile demo și la cele vechi.
 * `mustChangePassword` = contul a primit o parolă temporară de la Admin (aplicația cere imediat una nouă);
 * `hasRecoveryCode` = contul are cod de recuperare pentru „Am uitat parola”.
 */
public record UserDto(Long id, String email, String phone, String displayName, Role role, String locale, int pointsBalance, BusinessSummary business,
                      boolean mustChangePassword, boolean hasRecoveryCode) {
}
