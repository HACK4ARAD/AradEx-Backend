package aradexperience.dto.auth;

import aradexperience.enums.Role;

/** docs/API.md: UserDto. `business` e null pentru rolurile fără firmă; conturile personale au `phone` (E.164), cele de firmă/Primărie `email` (T21). */
public record UserDto(Long id, String email, String phone, String displayName, Role role, String locale, int pointsBalance, BusinessSummary business) {
}
